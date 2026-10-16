# Architectural Specification: Lazy Resource Decoder

**Component**: `LazyResourceDecoder` / `LazyResFiles` / `LazyXmlFiles`  
**Package**: `app.morphe.patcher.resource.coder`  
**Target Subsystem**: Resource Decompression, Heap Conservation & Concurrent Staging Pipeline  
**Status**: Authoritative Architectural Standard  

---

## 1. Executive Summary & Problem Statement

Android reverse-engineering frameworks traditionally decode APK resources via an eager, exhaustive strategy: all assets, drawables, fonts, and binary XML files are inflated and written to disk before any patching begins.

The measured YouTube build (next/research/02-pipeline-shape.md) has 17,491 decoded files and 293,795 resource table values. The baseline measurements run with `-Xmx640m` and 4 cores (next/research/02-pipeline-shape.md, timings section). Eager decoding under these limits causes the following problems:
1. **Heap Pressure & Unclosed Inflaters**: Upstream ARSCLib creates `InflaterInputStream` instances during binary XML decoding and does not close them. Their native zlib state is held until garbage collection, which adds to heap pressure.
2. **I/O Cost**: In the measured baseline, resource decoding took 36.8 s of a 144.5 s run (next/research/02-pipeline-shape.md, stage table). Patches touched 278 of 17,491 files (1.6%), so most of the decoded output was never read.
3. **Peak Memory Concurrency**: Retaining the fully parsed `ApkModule` resource table while multi-DEX bytecode is processed pushes peak heap toward the limit. The measured baseline peaked at 637 of 640 MB with "Large object allocation failed" (next/research/04-prior-art-and-pain.md).

`LazyResourceDecoder` solves these problems through a **Two-Stage Heap Policy** combined with a **Thread-Safe Worker Lock Architecture**.

---

## 2. Two-Stage Heap Policy

```
+---------------------------------------------------------------------------------------+
|                                    INPUT APK ARCHIVE                                  |
+---------------------------------------------------------------------------------------+
                                           |
                                           v
+---------------------------------------------------------------------------------------+
| STAGE 1: METADATA & ARSC INGESTION                                                    |
| - Parse tableBlock (resources.arsc)                                                   |
| - Discover package names, resource IDs, and type structures                           |
| - Skip native libraries (lib/*/*.so left in archive)                                  |
| - Defer all file writes to Lazy queues                                                |
+---------------------------------------------------------------------------------------+
                                           |
                    +----------------------+----------------------+
                    |                                             |
                    v (Raw Files: PNG, webp, font)                v (Binary XML Files)
+---------------------------------------+     +-----------------------------------------+
| LazyResFiles Subsystem                |     | LazyXmlFiles Subsystem                  |
| - Defers raw file writes              |     | - Defers XML decoding                   |
| - Workers at Thread.MIN_PRIORITY      |     | - Stream-scoped inflaters (auto-closed) |
| - On-demand awaitFor(File)            |     | - Early module release via seal()       |
+---------------------------------------+     +-----------------------------------------+
                    |                                             |
                    +----------------------+----------------------+
                                           |
                                           v
+---------------------------------------------------------------------------------------+
| STAGE 2: HEAP PRUNING & TIMELY RESOURCE RELEASE                                       |
| - Release ApkModule and TableBlock once seal() has run and no decode is pending       |
| - Close ZipFile handles once queues are empty and no reader or writer is active       |
| - Parsed module and table become eligible for garbage collection at that point        |
+---------------------------------------------------------------------------------------+
```

### 2.1 Stage 1: Metadata & ARSC Ingestion
During Stage 1, `LazyResourceDecoder` extracts only structural metadata required to plan patches:
- **Table Block Ingestion**: The binary `resources.arsc` chunk is parsed into `tableBlock`. Resource IDs, configuration qualifiers, and type symbols are indexed.
- **Native Library Bypass**: Root archive entries under `lib/` and DEX entries are not extracted to the filesystem, because `stagesRootEntry(...)` returns false for them (`ArsclibResourceCoder.kt:1276`). They remain in the archive and are copied zero-copy during packaging. In the measured APK, `lib/` is 320.8 MB uncompressed (next/research/02-pipeline-shape.md).
- **Deferral Queueing**: Resource files encountered during table traversal are classified:
  - *Binary XML files*: Registered with `LazyXmlFiles` for just-in-time DOM serialization.
  - *Raw non-XML files*: Handled by `LazyResFiles` for background asynchronous streaming.

### 2.2 Stage 2: Just-in-Time Materialization & Early Heap Teardown
- **Native Stream Closure**: Each binary XML entry stream is read inside a `use { ... }` block, both in the deferred path (`LazyXmlFiles.decode`) and in the eager path of `decodeXml`. The `InflaterInputStream` is closed after the file is read, so its native zlib state is released per file. Upstream ARSCLib does not close these streams (`LazyResourceDecoder.kt:484-488`).
- **Early Module Lifecycle Release**: `LazyXmlFiles` requires the parsed `ApkModule` only to resolve resource attributes during XML serialization. Once `seal()` has been called, no decode is pending, and no reader or writer is active, the module is closed and dropped (`LazyXmlFiles.releaseIfDone()`, `LazyResourceDecoder.kt:365-372`):
  $$\text{module.close}() \implies \text{module} = \text{null}$$
  The parsed AST and table block structures then become eligible for garbage collection. The reference is dropped so that this object does not keep the table reachable.
- **Input Archive Discard**: `LazyResFiles` closes its `ZipFile` in `releaseIfDone()` (`LazyResourceDecoder.kt:163-170`) once `start()` has run, no entry is pending or in flight, and the active readers and writers counter is zero. `LazyXmlFiles` applies the same counter rule to its `ZipFile` and module, once `seal()` has run and no decode is pending.
- **Counter gating**: Each open entry stream and each running write increments the active readers and writers counter, and its completion decrements the counter and signals waiters. A release that is due while the counter is above zero is deferred until the last stream or write finishes. This is what keeps the input archive and the module open while a read or write is still using them.

---

## 3. Concurrency Model & Worker Locks

```
                       +-----------------------------------+
                       |        Caller Thread              |
                       +-----------------------------------+
                             |                       ^
                 awaitFor()  |                       | Returns complete
                             v                       | directory/file
              +-------------------------------------------------+
              |            LazyResFiles ReentrantLock           |
              +-------------------------------------------------+
                 |            |                       |
                 | Claim      | Await                 | Work
                 v            v                       v
      +---------------+  +---------------+  +-------------------+
      | pending Map   |  | inFlight Set  |  | Background Worker |
      | (TreeMap)     |  | (HashSet)     |  | Pool (MIN_PRIOR)  |
      +---------------+  +---------------+  +-------------------+
                                                      |
                                                      v
                                            +-------------------+
                                            | Disk Write Pass   |
                                            | (ThreadLocal 64K) |
                                            +-------------------+
```

### 3.1 Lock Invariants & State Synchronization
Thread safety in `LazyResFiles` is maintained by a single `ReentrantLock` and condition variable `settled`:
- `pending`: `TreeMap<String, Pending>` keyed by the absolute target path (forward slashes). Each `Pending` holds the archive entry name and the target file. Sorted lexicographically to enable efficient sub-tree queries.
- `inFlight`: `HashSet<String>` of the same keys, for the files currently being written, by the caller or by a background worker.
- `failures`: `LinkedHashMap<String, Throwable>` capturing asynchronous I/O errors by target path until an await of that path reports them.
- The active readers and writers counter (`AtomicInteger`) is changed outside the lock by open streams and running writes. It gates the release described in section 2.2.

### 3.2 Dual-Mode Execution: Background vs Just-in-Time
1. **Background Daemon Worker Pool**:
   - Spawns $N$ daemon threads where $N = \text{clamp}(\text{availableProcessors}, 1, 3)$.
   - Threads run at `Thread.MIN_PRIORITY` by default. The system property `oru.patcher.rawWriterPriority` selects `min` (default) or `norm`. Any other value makes the decoder throw `IllegalArgumentException` (`LazyResourceDecoder.kt:35-40`).
   - Workers poll tasks from `pending`, add the path to `inFlight`, write to disk, remove from `inFlight`, and broadcast `settled.signalAll()`.
2. **Deterministic Caller Demanding (`awaitFor`)**:
   - When a patch accesses a file or directory via `get(path)` or lists entries:
     - Caller acquires the lock.
     - Performs a range scan on `pending` using the prefix (`path` and `path/*`).
     - Removes matching entries from `pending` and immediately claims them by adding to `inFlight`.
     - Releases the lock and writes the claimed entries directly on the calling thread.
     - If any matching entry is already being written by a background worker, the caller waits uninterruptibly on `settled` until `inFlight` no longer contains matching paths.
     - **Result**: The caller thread is guaranteed a complete, consistent file tree without race conditions.

### 3.3 ThreadLocal Copy Buffers
To avoid allocating a buffer for every file copy:
- Each thread utilizes a `ThreadLocal<ByteArray?>` caching a $64\text{ KiB}$ buffer (`COPY_BUFFER_SIZE = 64 * 1024`, `LazyResourceDecoder.kt:288`). A thread allocates its buffer on its first copy, when its `ThreadLocal` is empty.
- Buffers are detached from the `ThreadLocal` during the write pass and restored upon completion, preventing cross-thread data corruption and supporting reentrant/nested copy operations.

### 3.4 Change Detection Timestamp Backdating
Patches detect whether a resource was modified by comparing file modification timestamps against a baseline. If a deferred file were written with the current timestamp (`now`), change detectors would falsely flag it as modified.
- All files written by `LazyResFiles` and `LazyXmlFiles` have their timestamps explicitly backdated:
  $$\text{target.setLastModified}(\text{currentTimeMillis}() - 60{,}000\text{L})$$
- Any subsequent write by a patch updates the timestamp to `now`, accurately reflecting patch mutations.

---

## 4. Contract Verification & Fallback Guarantees

### 4.1 ARSCLib Reflective Method Handles
`LazyResourceDecoder` integrates with internal ARSCLib methods via cached reflection (`Handles`):
- `decodeTableBlock`, `decodeValues`, `decodeOverlayable`, `serializeXml`, `toDecodeResFile`, `addDecodedEntry`.

### 4.2 Graceful Fallback
If an ARSCLib internal method is missing, or reflection fails with a `ReflectiveOperationException` or `LinkageError`:
- `HANDLES` evaluates to `null` (`LazyResourceDecoder.kt:521-527`).
- `LazyResourceDecoder.decodeResourceTable()` calls `super.decodeResourceTable(...)` (eager decode, `LazyResourceDecoder.kt:448-451`).
- No error is thrown and nothing is logged, so the fallback is silent.

No test exercises this fallback. `LazyResourceDecoderContractTest.kt` checks that the decoder reports itself as supported when the methods exist, and does not run the eager path.

### 4.3 Contract Testing
`LazyResourceDecoderContractTest.kt` verifies the reflective bindings. It checks that every private ARSCLib method the decoder reflects on exists with the exact parameter types, and it names the missing method when one does not. A signature change in an ARSCLib update therefore fails these tests.
