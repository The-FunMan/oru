# native-packager

`native-packager` is a high-performance native packaging engine written in Rust that writes Android Application Packages (APKs) in a single sequential pass and signs them in place. It replaces the legacy multi-step `resources.apk` build, file copy, `zipalign`, and `apksigner` pipeline in Morphe Patcher with a unified, zero-copy native implementation.

The patcher interacts with this engine via JNI (`jni/`, `oru_packager`), built across all four major Android ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`) via `buildNativePackager`.

---

## Formal Architectural Specification

```
+---------------------------------------------------------------------------------------+
|                                    INPUT APK (mmap)                                   |
+---------------------------------------------------------------------------------------+
           |                                                      |
           v (untouched entries)                                  v (entries to replace/add)
+-----------------------+                              +-----------------------+
| Zero-Copy Byte Slice  |                              | Parallel Deflate Pool |
| Direct Stream Copy    |                              | (Level 3, <= 8 cores) |
+-----------------------+                              +-----------------------+
           |                                                      |
           +--------------------------+---------------------------+
                                      |
                                      v
                      +-------------------------------+
                      | 16 KiB Page Alignment Engine  |
                      | - 16 KiB for lib/*/*.so (ELF) |
                      | - 4 bytes for resources.arsc  |
                      | - 0xd935 padding extra fields |
                      +-------------------------------+
                                      |
                                      v
                      +-------------------------------+
                      | Sequential Pass Output Writer |
                      | (Buffered ZIP stream)         |
                      +-------------------------------+
                                      |
                                      v
                      +-------------------------------+
                      | APK Signature Scheme v2       |
                      | - 1 MiB chunked digest        |
                      | - Deterministic SHA-256 tree  |
                      | - In-place APK Signing Block  |
                      +-------------------------------+
                                      |
                                      v
+---------------------------------------------------------------------------------------+
|                                OUTPUT SIGNED APK                                      |
+---------------------------------------------------------------------------------------+
```

### 1. Native Zero-Copy Pipeline

Legacy Android repackaging processes extract APK entries to disk, modify target files, compress each file individually, repackage a new ZIP archive, and subsequently run standalone alignment and signing binaries. This causes excessive disk I/O, cache thrashing, and high CPU overhead on mobile devices.

`native-packager` eliminates this overhead via a memory-mapped, single-pass pipeline:

- **Read-Only Memory Mapping**: The input APK archive is memory-mapped via `memmap2::Mmap`. The Central Directory is read once through `zip::ZipArchive`. The declared entry count is compared with the number of readable entries, and a mismatch (which indicates duplicate entry names) is an error.
- **Zero-Copy Untouched Entry Forwarding**: For all entries not targeted by modification or deletion in the execution plan (`Plan`), their raw compressed payloads are copied directly from the memory map to the output `BufWriter` stream. These payloads are never inflated or recompressed, which preserves the original Deflate bitstreams. The CRC-32 is copied from the input Central Directory and is not re-checked against the payload. The one exception is a deflated `resources.arsc`, which is inflated and rewritten as `STORED` (see section 2).
- **Execution Plan & Mutation Engine**: The engine accepts an operational `Plan`:
  - `Plan::remove`: Targeted entry names dropped from the output stream.
  - `Options::entries`: Files on disk whose contents replace existing entries or are appended as new entries.
  - Replaced entries keep the compression method (`STORED` vs `DEFLATED`) of the input entry. New entries are deflated. `resources.arsc` and `lib/*/*.so` entries default to `STORED`, whether replaced or new. `Plan::stored` overrides the default by entry name.
- **Parallel Deflate Pool**: Newly staged and replaced entries requiring compression are deflated in parallel across a thread pool bounded to `min(cores, 8)` worker threads using `flate2` at compression level 3. If deflating an entry does not yield a smaller byte size than uncompressed storage, the engine automatically falls back to `METHOD_STORED`, reproducing standard Android build-tool heuristics (`apkzlib`).

---

### 2. 16 KiB Page Alignment Architecture

Starting with Android 15 (API 35), the Android runtime supports and enforces 16 KiB (16,384 bytes, `0x4000`) physical memory page sizes on 64-bit architectures. Shared libraries (`.so`) mapped directly from uncompressed APK archives (`android:extractNativeLibs="false"`) must be aligned to 16 KiB boundaries; otherwise, `mmap(2)` fails at process startup with `EINVAL`.

`native-packager` guarantees strict alignment during the sequential write pass without requiring post-processing passes:

#### Alignment Invariant
For any stored entry with alignment constraint $A$:
$$\text{DataOffset} \equiv 0 \pmod A$$
where:
$$\text{DataOffset} = \text{FileOffset} + \text{Len}(\text{LocalHeader}) + \text{Len}(\text{FileName}) + \text{Len}(\text{ExtraField})$$

#### Subsystem Specifications
1. **ELF Shared Libraries (`lib/*/*.so`)**: Entries whose names start with `lib/` and end in `.so` are enforced to 16 KiB ($16{,}384$ bytes) alignment when stored uncompressed.
2. **Resource Tables (`resources.arsc`)**: Enforced to 4-byte alignment when stored uncompressed. If the input APK contains a deflated `resources.arsc`, `native-packager` inflates and rewrites it as `STORED` to allow direct zero-copy memory mapping by Android's runtime `AssetManager`.
3. **General Stored Assets**: All other `METHOD_STORED` entries are aligned to 4-byte boundaries.
4. **Android Alignment Extra Field (`0xd935`)**:
   - To achieve alignment, the Local File Header's extra field is padded using the standard Android alignment header ID `0xd935`.
   - The extra field structure is formatted as:
     - `Header ID`: `0xd935` (2 bytes, little-endian)
     - `Data Size`: $N$ (2 bytes, little-endian, where $N \ge 0$)
     - `Padding`: $N$ null bytes (`0x00`)
   - The padding is calculated before writing the Local File Header. Let $H = \text{CurrentOffset} + \text{HeaderBaseLen} + \text{NameLen}$, the offset where the extra field starts. If $H \bmod A = 0$, the entry has no extra field. Otherwise the extra field length is $E = A - (H \bmod A)$, or $E + A$ when $E < 4$, since the header itself is 4 bytes. The padding is then $N = E - 4$ bytes, and `Data Size` holds $N$.
   - The central directory writes an extra field length of zero for every entry. The alignment padding appears only in the Local File Headers.

---

### 3. APK Signature Scheme v2 Architecture

`native-packager` implements full in-place signing (`sign_in_place`) for APK Signature Scheme v2 (v2 block ID: `0x7109871a`). Signing does not require an archive re-write; instead, the signature block is written directly between the ZIP entries payload and the Central Directory.

#### Section Partitioning & Deterministic Chunked Digests
The output file is partitioned into three distinct cryptographic sections:
1. **Section 1: ZIP Entries**: From byte offset 0 up to the start of the APK Signing Block.
2. **Section 2: Central Directory**: The complete ZIP Central Directory.
3. **Section 3: End of Central Directory (EOCD)**: The 22-byte (or variable comment length) EOCD record. In the digest, its "Central Directory starting offset" field is set to the start of the APK Signing Block. The EOCD written to the file has that field set to the start of the Central Directory, which follows the block.

#### Merkle-Tree Digest Algorithm
- Each section is split into fixed $1\text{ MiB}$ ($1{,}048{,}576$ byte) chunks.
- Each chunk is hashed using SHA-256 with a 5-byte chunk header prefix:
  $$\text{ChunkDigest} = \text{SHA-256}\left(\texttt{0xa5} \parallel \text{len}_{\text{u32LE}}(\text{chunk}) \parallel \text{chunk\_bytes}\right)$$
- Chunk digests of a section are computed in parallel. Each section uses $\min(\text{cores}, 8, \lfloor M / 4 \rfloor)$ workers, where $M$ is the chunk count of the section, and a section with fewer than eight chunks is hashed on the calling thread. Results are stored by absolute chunk index, so the output does not depend on the thread count:
  $$\text{SectionDigest} = \left[ \text{ChunkDigest}_0, \text{ChunkDigest}_1, \ldots, \text{ChunkDigest}_{M-1} \right]$$
- There is one top-level digest for the whole file. The chunk digests of the three sections are concatenated in section order (entries, Central Directory, EOCD), and $N$ is the total number of chunk digests:
  $$\text{TopDigest} = \text{SHA-256}\left(\texttt{0x5a} \parallel \text{count}_{\text{u32LE}} \parallel \text{ChunkDigest}_0 \parallel \ldots \parallel \text{ChunkDigest}_{N-1}\right)$$
  There is no per-section top digest and no separate digest over the sections.

#### In-Place Signing Block Format
The APK Signing Block is formatted according to the Android v2 specification:
- `Size of Block` (uint64, LE): Total size of the block excluding this field.
- **ID-Value Pairs**:
  - `Pair Size` (uint64, LE)
  - `Pair ID`: `0x7109871a` (APK Signature Scheme v2)
  - `Signer Sequence`:
    - `Signed Data`: One content digest (algorithm ID and `TopDigest`), the X.509 certificate (DER format), and an empty list of additional attributes.
    - `Signatures`: Cryptographic signature over `Signed Data`.
    - `Public Key`: SubjectPublicKeyInfo encoding of the signer public key.
- `Size of Block` (uint64, LE): Identical to the initial size field.
- `Magic String`: Fixed 16-byte magic footer `APK Sig Block 42` (`41 50 4b 20 53 69 67 20 42 6c 6f 63 6b 20 34 32`).

#### Supported Cryptographic Algorithms
- `0x0103`: RSA with PKCS#1 v1.5 padding and SHA-256.
- `0x0201`: ECDSA over NIST P-256 with SHA-256 (DER-encoded ASN.1 signature).

---

## Memory Safety & JNI Lifecycle

`native-packager` exposes its interface to the JVM via `jni/src/lib.rs`:
- **Key Material Lifetimes**: Private keys are transferred across JNI as raw PKCS#8 DER byte arrays. The JVM caller zeroes its encoded-key array in a `finally` block after the native call returns. The native side copies the bytes, overwrites that copy with zeros after signing, and drops the parsed signer when signing returns.
- **Error Propagation**: Every JNI entry point returns `0` and throws `java.io.IOException` on failure. A Rust panic is caught by the same guard and thrown as `java.io.IOException`. Open memory maps and file handles are released when the Rust call returns, through drop.

---

## CLI Usage

```text
cargo build --release
native-packager --input stock.apk --output signed.apk \
  --key key.pk8.pem --cert cert.pem \
  [--entry lib/arm64-v8a/libfoo.so=libfoo.so] [--entry assets/a.txt=a.txt]
```

- `--key`: PKCS#8 PEM private key (RSA 2048/4096 or ECDSA P-256). The JNI wrapper accepts raw PKCS#8 DER bytes.
- `--cert`: X.509 certificate in PEM format.
- Output summary and compression metrics are printed to `stderr`.

### Verification with Official Android Tools

Validate packaging alignment and v2 signature compliance using official Android SDK build-tools:

```bash
# Verify v2 signature validity
apksigner verify -v --min-sdk-version 24 signed.apk

# Verify 16 KiB (P 16) native library alignment and 4-byte general alignment
zipalign -c -P 16 -v 4 signed.apk
```

---

## Testing & Parity Verification

```bash
# Unit tests with synthetic archives and generated test keys
cargo test

# Full parity test comparing native-packager against reference apksigner/zipalign on real APKs
cargo test --release --test parity -- --ignored
```

- **Synthetic Benchmarks**: `tests/bench.rs` measures deflate and packaging throughput on randomized byte payloads.
- **PowerShell Benchmark Suite**: `bench/bench.ps1` benchmarks end-to-end throughput against `zipalign` combined with `apksigner sign`.

---

## Architectural Constraints & Non-Goals

- **File Limits**: Input and output APKs must not exceed 4 GiB or 65,535 entries (ZIP64 extensions are unsupported and incompatible with Android package managers).
- **Encryption**: Encrypted ZIP entries are rejected with an explicit error.
- **Signature Schemes**: Exclusively implements APK Signature Scheme v2 (v1 JAR signing and v3/v4 signing are intentionally omitted).
- **Entry Names**: ZIP entry names must be valid UTF-8 strings.
