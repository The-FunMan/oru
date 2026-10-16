# Parity tool and patch run profiling

Phase 0 tools for Oru: a parity checker for two APKs (`parity.py`) and a desktop run of the
patcher with an opt-in phase log and counters (`patcher/src/test/.../profile/ProfileRun.kt`).

Paths below are for the Oru worktree; adjust them for another checkout.

## parity.py

Python 3 standard library only. It needs SDK build-tools 36.0.0 (`aapt2`, `lib/apksigner.jar`) and
`java` on `PATH` for the signing check. Build-tools are looked up under `ANDROID_HOME`, or pass `--build-tools`.

```
python tools/parity/parity.py <a.apk> <b.apk> [--allow FILE] [--json FILE] [--limit N] [--no-signing]
```

Exit code: `0` when every difference is allowed, `1` when at least one is not, `2` on usage errors.

What it compares:

| Category | Meaning |
|---|---|
| `zip.added`, `zip.removed` | entry names present in only one APK (all entries) |
| `zip.changed` | content hash differs (not for `classes*.dex`, `resources.arsc`, META-INF signature files) |
| `dex.added`, `dex.removed` | class descriptors present in only one APK (read with a pure-Python dex reader) |
| `arsc.added`, `arsc.removed` | resource `type/name` present in only one table (from `aapt2 dump resources`) |
| `arsc.id.changed` | same resource name, different resource id |
| `arsc.value.changed` | same resource name, different config set or value text |
| `signing.verify`, `signing.cert` | `apksigner verify --print-certs` result or certificate lines differ |

Informational only, shown in the report and never a failure: compression state of entries
(`zip.compression`), data alignment (`zip.alignment`, 4 bytes, and 16 KB for `lib/*.so`), classes that
moved between dex files (`dex.moved`), and META-INF signature files that differ (`signing.files`, their bytes are
ignored because they change with every signing).

Allow-list (`--allow FILE`): one rule per line, `<category-glob> <subject-glob>`, split on the first
whitespace. Blank lines and lines starting with `#` are ignored. Each rule is matched with `fnmatch`
against a difference's category and subject. Example:

```
# Expected Morphe output
zip.added classes*.dex
zip.changed res/values/*
arsc.value.changed *
```

Keep the allow-list narrow. An empty allow-list is the default and the strictest setting.

Subjects for `dex.*` are class descriptors (for example `Lcom/example/Foo;`), for `arsc.*` are `type/name`,
and for `zip.*` are entry names. `--json FILE` writes every difference with its detail.

### Checks run for this tool

```
python -m unittest discover -s tools/parity -p "test_*.py" -v
```

The tests build APKs and dex files in code (no real app files). Resource tests run the real `aapt2`
on small generated tables when it is available, and are skipped otherwise.

Measured on the stock YouTube 21.40.161 APK (copied to scratch first) and the desktop output of this worktree
(see REPORT.md):

```
python tools/parity/parity.py <stock.apk> <stock.apk>      # exit 0, 0 differences
python tools/parity/parity.py <stock.apk> <patched.apk>    # exit 1, differences listed
python tools/parity/parity.py <patched-run1.apk> <patched-run2.apk>   # only signing.cert differs
```

## Profiling a desktop patch run

`ProfileRun` mirrors the manager's `Session`: load the bundle's default patches compatible with
`com.google.android.youtube`, run them, write the APK with `ApkUtils.applyTo`, and sign it.

Profiling is off unless `-Doru.profile=true` is set. When on, the log gets one `oru.profile phase=...` line per
phase boundary (wall time since the first profiler call, time in that phase, live heap after a forced GC), then
counters and peak heap at the end. Heap peak is sampled every 20 ms and includes garbage not yet collected.

1. Compile the patcher tests and export the test runtime classpath (writes the file to `<cp file>`):

   ```
   cd patcher
   ./gradlew -I ../tools/parity/print-test-classpath.gradle -PoruCpOut=<cp file> compileTestKotlin oruPrintTestClasspath
   ```

2. Run it with the emulator's limits (640 MB heap, 4 processors):

   ```
   java -Xmx640m -XX:ActiveProcessorCount=4 -Doru.profile=true -cp "$(cat <cp file>)" \
     app.morphe.patcher.profile.ProfileRunKt \
     <stock.apk> <bundle.mpp> <out.apk> <work dir> <keystore file>
   ```

   The keystore file is created on first use. Use a scratch directory for `<work dir>` and `<out.apk>`.
   The stock APK and bundle are read in place and never written.

The profiler's call sites are `Patcher.invoke` and `Patcher.get` (phases), `Fingerprint.kt` (full class-scan fallbacks),
`ArsclibResourceCoder` (ApkModule loads), and `Session.run` in the manager (write and counters; the manager replaces
the root log handlers during a run, so its `logLines` count is not valid).

Counters:

- `arsc.load.<site>`: calls to `ApkModule.loadApkFile` on the input APK, per call site. This counts loads, not parses.
  Whether ARSCLib parses `resources.arsc` eagerly on load is not checked here.
- `fingerprint.fullScan.match`, `fingerprint.fullScan.matchAll`: fingerprint matches that fell back to scanning all classes.
- `logLines`: log records published through the root handlers while the profiler is loaded.

## patch_api_guard.py

Checks that patch bundles only reference `app/morphe/patcher` API that the patcher build actually contains.
Patches are upstream JVM classes in `.mpp` bundles that run against this patcher at runtime. Deleting or renaming a
public patcher class or member still compiles, and the failure only shows when a bundle loads
(`NoClassDefFoundError` for `MutableLongEncodedValue` was the symptom of the encoded value purge). The tool reads
constant pools only and needs no build tools.

```
python -I tools/parity/patch_api_guard.py --patcher <patcher.jar or classes dir> --bundle <patches.mpp> [--bundle ...]
```

Exit code: `0` clean, `1` unresolved references (each listed with up to three bundle classes that use it), `2` usage
error, or a bundle with no class files (a dex-only bundle is reported and not checked).

Run it before merging any change that deletes or changes public patcher API (classes, methods, fields, signatures),
against the latest dev and the latest stable patch bundles, using a freshly built patcher jar.

Scope and limits: class files only. Dex entries such as `extensions/*.mpe` are counted and not scanned. A reference
that reaches a supertype outside the patcher is assumed to resolve, except that `java/lang/Object` is checked against
its known public methods; those supertypes are listed in a note.

Tests (synthetic class files, no bundles needed):

```
python -I -m unittest discover -s tools/parity -p "test_patch_api_guard.py" -v
```
