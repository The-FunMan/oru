# Architectural Specification: Incremental Resource Encoder

**Component**: `IncrementalResourceEncoder`  
**Package**: `app.morphe.patcher.resource.coder`  
**Target Subsystem**: Android Binary Resource Table (`resources.arsc`) Compilation & Compaction  
**Status**: Authoritative Architectural Standard  

---

## 1. Executive Summary & Problem Statement

In standard Android reverse engineering and patching pipelines (such as `apktool` or unoptimized `ARSCLib` passes), modifying application resources requires a complete decode-and-rebuild cycle. The entire resource table is serialized into tens of thousands of filesystem XML documents, and subsequently rebuilt from scratch into a new `resources.arsc` binary chunk stream.

For large-scale production applications (e.g., YouTube with >290,000 values and >16,000 resource files), this legacy approach introduces three catastrophic architectural failure modes:
1. **Severe Memory Bloat**: Re-encoding the entire resource AST requires heap allocations exceeding 1.5–2.0 GiB, routinely causing out-of-memory (`OutOfMemoryError`) crashes in memory-constrained mobile environments (`-Xmx640m`).
2. **Excessive Processing Latency**: Full rebuilds spend 30 to 60+ seconds parsing and serializing millions of unchanged XML nodes and string pool entries.
3. **Format Degradation & Semantic Drift**: Rebuilding upstream compiled resource tables loses vendor-specific compiler flags, reshuffles string pools, strips subtle type qualifiers, and risks runtime resource resolution poisoning.

Because individual patches modify only a minuscule fraction (<2%) of an application's total resources, rebuilding the remaining 98% is fundamentally inefficient.

`IncrementalResourceEncoder` replaces full rebuilding with **Binary Table Authority**: the input APK's precompiled `resources.arsc` table is preserved as the single immutable authority for all unmodified data, selectively updating only the specific types, configurations, and chunks touched by patches.

---

## 2. Architectural Pipeline Overview

```
+---------------------------------------------------------------------------------------+
|                                    INPUT APK TABLE                                    |
|                             (tableBlock = module.tableBlock)                          |
+---------------------------------------------------------------------------------------+
                                           |
                                           v
                  +-------------------------------------------------+
                  | Stage 1: Delta Identification                   |
                  | - Modified values XML files                     |
                  | - Deleted values files & deleted archive entries|
                  | - Patched configuration directories             |
                  | - Newly declared public IDs (public.xml)        |
                  +-------------------------------------------------+
                                           |
                                           v
                  +-------------------------------------------------+
                  | Stage 2: ID Declaration & Namespace Alignment   |
                  | - packageBlock.declareIds(publicIds)            |
                  | - Synchronize package ID & type strings         |
                  | - Synthetic type "id" entry initialization      |
                  +-------------------------------------------------+
                                           |
                                           v
                  +-------------------------------------------------+
                  | Stage 3: Topological Incremental Encoding       |
                  | 1. attrs.xml (Enums & attribute definitions)    |
                  | 2. Remaining values files (strings, colors, etc)|
                  | 3. Deleted value emptying (entry.empty())       |
                  | 4. Patched configurations (sparse types)        |
                  +-------------------------------------------------+
                                           |
                                           v
                  +-------------------------------------------------+
                  | Stage 4: Sparse Compaction Engine               |
                  | - PackageBlock.compactSparseTypeBlocks()        |
                  | - Prune null / 0-offset entries from pairs      |
                  | - Rebuild OffsetReferenceList                   |
                  +-------------------------------------------------+
                                           |
                                           v
                  +-------------------------------------------------+
                  | Stage 5: Table Sanitization & Alignment         |
                  | - tableBlock.stringPool.removeUnusedStrings()   |
                  | - tableBlock.refresh()                          |
                  | - Re-encode AndroidManifest.xml (STORED)        |
                  +-------------------------------------------------+
                                           |
                                           v
+---------------------------------------------------------------------------------------+
|                             OUTPUT INCREMENTAL ARSC TABLE                             |
+---------------------------------------------------------------------------------------+
```

---

## 3. Subsystem 1: Binary Table Authority

### 3.1 Immutable Authority Principle
The input application's compiled `TableBlock` is directly loaded into memory and manipulated in place. Unmodified entries, types, spec configurations, and string pool blocks are retained byte-for-byte in their original binary representation.

### 3.2 Delta Identification & Classification
During patch execution, all resource modifications written to disk are captured and partitioned:
- `modifiedResources`: Files under `res/` added or modified by patches.
- `deletedEntries`: Archive entry names explicitly marked for removal via `delete(...)`.
- `deletedValuesFiles`: Decoded values files deleted by patches (e.g., stripping translation configurations like `values-es/strings.xml`).
- `patchedConfigurations`: Entire configuration directories added by patches (e.g., `values-night/`, custom locale overlays).

### 3.3 Dynamic Resource ID Declaration (`declareIds`)
Patches frequently introduce novel resource IDs or rename existing symbols in `public.xml`. `IncrementalResourceEncoder` synchronizes the `PackageBlock` against the patched `publicIds` mapping:
1. **Package Scope Verification**: Verifies `(resourceId ushr 24) == packageBlock.id`. IDs targeting foreign packages (e.g., android framework `0x01`) are bypassed.
2. **Dynamic Entry Creation**: If an ID does not exist in the table:
   - Resolves or creates the corresponding `typeId = (resourceId shr 16) and 0xff` in the type string pool.
   - Instantiates an unpopulated `Entry` at `resourceId and 0xffff` within the default (empty qualifier) `TypeBlock`.
   - Binds the resource name to the entry.
3. **Synthetic ID Allocation**: For resources of type `id`:
   - Because `id` entries represent pure programmatic identifiers with no underlying resource file or values XML node, they are initialized immediately:
     $$\text{entry.setValueAsBoolean}(\text{false}), \quad \text{header.isPublic} = \text{true}, \quad \text{header.isWeak} = \text{true}$$

### 3.4 Topological Encoding Order
Values files cannot be encoded in arbitrary order. `IncrementalResourceEncoder` enforces strict topological ordering:
1. **Attributes First (`attrs.xml`)**:
   - `attrs.xml` defines attribute IDs, custom styleable attributes, and enum/flag bitmask values.
   - All subsequent styles, themes, and complex XML structures reference attribute IDs. Re-encoding `attrs.xml` first ensures that downstream lookups in `XmlCoder` resolve against an updated attribute namespace.
2. **Remaining Values Files**:
   - Encoded directly into their respective `TypeBlock` using `XmlCoder.getInstance().VALUES_XML.encode(...)`.
3. **File-Defined Resource Preservation Invariant**:
   - An entry whose value references a file path (e.g., `@drawable/ic_launcher -> res/drawable/ic_launcher.png`) was never declared in a values file.
   - If a values file being updated does not declare that symbol, the symbol is **not** deleted:
     $$\text{isDefinedByFile}(e) \iff e.\text{resValue}.\text{type} == \text{STRING} \land \text{archive.contains}(e.\text{resValue}.\text{asString})$$

---

## 4. Subsystem 2: Sparse Compaction Architecture

### 4.1 The Sparse Type Block Binary Format
In standard (dense) Android resource tables, a `ResTable_type` chunk defines an array of 32-bit offsets indexed by entry ID ($0, 1, \dots, N-1$). For sparse tables (introduced in Android 8.0, AAPT2), the flag `FLAG_SPARSE = 0x01` is asserted on the type chunk header.

A sparse `TypeBlock` encodes entries as an array of `ResTable_sparseTypeEntry` structures:
```c
struct ResTable_sparseTypeEntry {
    uint16_t entry_idx;  // Entry index within type (0 to 65535)
    uint16_t offset;     // Offset to ResTable_entry in 4-byte words
};
```
This reduces APK size significantly for localized or partial configurations where only a few dozen of thousands of potential entries are defined.

### 4.2 The Dangling Null Anomaly
When a patch empties or deletes an entry within a configuration (`entry.empty()`), leaving the entry in a sparse `TypeBlock` corrupts Android's runtime resource resolution:
1. In a sparse table, an empty/null entry has its offset cleared to 0.
2. At runtime, Android's `AssetManager` searches the `ResTable_sparseTypeEntry` array.
3. An offset of 0 is treated as a valid pointer to byte 0 of the entries data block—which corresponds to the **first defined resource** in that chunk.
4. **Resulting Defect**: Runtime lookups for deleted resource IDs succeed erroneously, returning whichever arbitrary surviving resource sits at offset 0. When an application attempts to load an expected string or drawable, it receives an incompatible object, triggering runtime `Resources.NotFoundException` or fatal `ClassCastException` crashes.

### 4.3 Sparse Compaction Invariant & Algorithm
To guarantee structural integrity, `compactSparseTypeBlocks()` enforces the invariant that **no sparse chunk may contain null entries or unreferenced indices**.

```
Algorithm: compactSparseTypeBlocks(PackageBlock)
------------------------------------------------
For each specTypePair in PackageBlock.listSpecTypePairs():
    For each typeBlock in specTypePair:
        If NOT typeBlock.isSparse:
            Continue

        entries := typeBlock.entryArray
        definedIds := []

        // Step 1: Harvest defined entry indices
        For index := 0 to entries.size() - 1:
            entry := entries.get(index)
            If entry != null AND NOT entry.isNull:
                definedIds.append(entry.id)

        // Step 2: Skip if already fully compacted
        If definedIds.size() == entries.size():
            Continue

        // Step 3: Rebuild OffsetReferenceList
        pairs := entries.getOffsetReferenceList()
        pairs.clearChildes()
        pairs.setSize(definedIds.size())

        For i := 0 to definedIds.size() - 1:
            pairs.get(i).idx := definedIds[i]

        // Step 4: Prune null entry objects
        entries.removeIf(entry -> entry.isNull)
```

By reconstructing the `OffsetReferenceList` exactly matching surviving IDs and purging null entries, the chunk size is minimized and offset resolution ambiguity is eliminated.

---

## 5. Subsystem 3: Table Sanitization & Finalization

### 5.1 String Pool Pruning
When entire language configurations or resource entries are removed (e.g., stripping non-English languages to reduce package size), string references disappear from type blocks.
`tableBlock.stringPool.removeUnusedStrings()` performs a mark-and-sweep GC pass over the global table string pool:
1. Traverses all active `Entry` values across all configurations.
2. Identifies unreferenced string pool indices.
3. Prunes unused strings and compacts the index mapping.

### 5.2 Header Refresh & Span Alignment
Styled strings (HTML formatted spans, `ResStringPool_span`) require strict 4-byte alignment following the string pool data payload. `tableBlock.refresh()` recomputes:
- Total chunk size fields across all headers.
- String pool offset tables and span offsets.
- Type spec bitmasks and type chunk header lengths.

### 5.3 AndroidManifest Synchronization
If `AndroidManifest.xml` was modified (e.g., package renaming, permission removal, application flags):
1. The XML document is encoded to binary XML format using `XMLEncodeSource` bound to the active `PackageBlock`.
2. The compression method is explicitly set to `Archive.STORED` (`method = 0`), ensuring zero-compression alignment and immediate mmap readiness in the final APK container.

---

## 6. Verification & Contract Testing

The formal behavior of `IncrementalResourceEncoder` is covered by the test suite in `IncrementalResourceEncoderTest.kt`:
- **Parity with Full Rebuilding**: Verified identical resource resolution across 16 integration scenarios.
- **Sparse Compaction Invariant**: Verified that deleting arbitrary sparse resources results in zero offset-0 resolution aliasing.
- **Attribute Precedence**: Verified that newly introduced custom styles depending on newly added attributes in `attrs.xml` compile without unresolved symbol errors. No test is named for this; it is exercised inside `edited resources are encoded over the input table`.
