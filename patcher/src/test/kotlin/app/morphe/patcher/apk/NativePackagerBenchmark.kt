package app.morphe.patcher.apk

import app.morphe.patcher.PatcherResult
import app.morphe.patcher.apk.ApkUtils.applyTo
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.MessageDigest
import java.util.Date
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.time.measureTime

/**
 * Times the write and sign tail, JVM against native, on a real APK. Run with
 * `-Poru.bench=true --tests "*NativePackagerBenchmark*"`, with `ORU_TEST_APK` (an APK whose
 * resources and dex files are the input) and `ORU_NATIVE_PACKAGER_LIB` set. Outputs of the last
 * run of each path are kept in `ORU_BENCH_OUT` for `apksigner` and `zipalign` checks.
 */
internal class NativePackagerBenchmark {
    @TempDir
    lateinit var temporaryDirectory: File

    @Test
    fun `tail time on the JVM and native paths`() {
        val apk = System.getenv("ORU_TEST_APK")?.let(::File)
        val library = System.getenv("ORU_NATIVE_PACKAGER_LIB")
        assumeTrue(apk?.isFile == true && library != null, "ORU_TEST_APK and ORU_NATIVE_PACKAGER_LIB are not set")
        System.setProperty("oru.packager.lib", library)
        val outputDirectory = System.getenv("ORU_BENCH_OUT")?.let(::File)?.also { it.mkdirs() }
        val signingKey = ApkSigner.newPrivateKeyCertificatePair("Bench", Date(System.currentTimeMillis() + 86_400_000L))
        val dexFiles = readDexFiles(apk!!)
        val replacements = replacementDirectory(apk)

        val samples = mapOf("jvm" to mutableListOf<Pair<Long, Long>>(), "native" to mutableListOf<Pair<Long, Long>>())
        val runs = 5
        // One warm-up pair, then alternating runs so that drift on a shared host hits both paths alike.
        for (run in 0..runs) {
            for (path in samples.keys) {
                val resources = temporaryDirectory.resolve("$path-$run.apk").also { apk.copyTo(it) }
                val patched = PatcherResult(
                    dexFiles.mapTo(LinkedHashSet()) { (name, bytes) ->
                        PatcherResult.PatchedDexFile(name, bytes.inputStream())
                    },
                    PatcherResult.PatchedResources(resources, replacements, emptySet(), emptySet()),
                )
                System.setProperty("oru.packager", path)
                try {
                    val writeMs = measureTime { patched.applyTo(resources) }.inWholeMilliseconds
                    val signMs = measureTime {
                        ApkSigner.newApkSigner("Bench", signingKey).signApk(resources, resources, MIN_SDK)
                    }.inWholeMilliseconds
                    if (run > 0) samples.getValue(path) += writeMs to signMs
                    println("BENCH $path run=$run write=${writeMs}ms sign=${signMs}ms")
                    if (run == runs && outputDirectory != null) {
                        resources.copyTo(outputDirectory.resolve("$path.apk"), overwrite = true)
                    }
                } finally {
                    System.clearProperty("oru.packager")
                }
            }
        }

        for ((path, pairs) in samples) {
            val writes = pairs.map { it.first }.sorted()
            val signs = pairs.map { it.second }.sorted()
            println("BENCH median $path write=${writes[writes.size / 2]}ms sign=${signs[signs.size / 2]}ms (n=${pairs.size})")
        }
        outputDirectory?.let(::compareOutputs)
    }

    /** The stock dex files, read once so that timing excludes reading them from the APK. */
    private fun readDexFiles(apk: File): List<Pair<String, ByteArray>> = ZipFile(apk).use { zip ->
        zip.entries().asSequence()
            .map { it.name }
            .filter { DEX_NAME.matches(it) }
            .map { name -> name to zip.getInputStream(zip.getEntry(name)).use { it.readBytes() } }
            .toList()
    }

    /** Three resources replaced from a directory, as a patch run replaces a few. */
    private fun replacementDirectory(apk: File): File {
        val directory = temporaryDirectory.resolve("replacements").apply { mkdirs() }
        ZipFile(apk).use { zip ->
            zip.entries().asSequence()
                .map { it.name }
                .filter { it.startsWith("assets/") && !it.endsWith("/") }
                .take(REPLACED_COUNT)
                .forEach { name -> directory.resolve(name).apply { parentFile.mkdirs() }.writeText("replaced $name") }
        }
        return directory
    }

    /** Compares the entry list, method and content of the two kept outputs. */
    private fun compareOutputs(directory: File) {
        val jvm = directory.resolve("jvm.apk")
        val native = directory.resolve("native.apk")
        if (!jvm.isFile || !native.isFile) return
        val jvmEntries = entries(jvm)
        val nativeEntries = entries(native)
        println("BENCH entries jvm=${jvmEntries.size} native=${nativeEntries.size} sameNames=${jvmEntries.keys == nativeEntries.keys}")
        println("BENCH method differences: ${jvmEntries.keys.filter { jvmEntries[it]?.first != nativeEntries[it]?.first }}")
        println("BENCH content differences: ${jvmEntries.keys.filter { jvmEntries[it]?.second != nativeEntries[it]?.second }}")
    }

    /** Entry name to (method, SHA-256 of the content), so that large outputs are compared without loading them. */
    private fun entries(file: File): Map<String, Pair<Int, String>> = ZipFile(file).use { zip ->
        zip.entries().asSequence().associate { entry ->
            val digest = if (entry.isDirectory) {
                "directory"
            } else {
                val sha = MessageDigest.getInstance("SHA-256")
                zip.getInputStream(entry).use { input ->
                    val buffer = ByteArray(1 shl 16)
                    generateSequence { input.read(buffer).takeIf { it >= 0 } }.forEach { sha.update(buffer, 0, it) }
                }
                sha.digest().joinToString("") { "%02x".format(it) }
            }
            entry.name to (entry.method to digest)
        }
    }

    private companion object {
        const val MIN_SDK = 29
        const val REPLACED_COUNT = 3
        val DEX_NAME = Regex("""classes\d*\.dex""")
    }
}
