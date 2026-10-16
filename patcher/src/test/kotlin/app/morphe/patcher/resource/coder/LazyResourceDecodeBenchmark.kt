package app.morphe.patcher.resource.coder

import app.morphe.patcher.util.PhaseProfiler
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test

/**
 * Manual benchmark, skipped unless ORU_BENCH_APK names an APK. Prints one line per measurement.
 * Counters (binary XML, raw files) are printed when JAVA_TOOL_OPTIONS sets oru.profile=true.
 */
internal class LazyResourceDecodeBenchmark {
    private val apkPath = System.getenv("ORU_BENCH_APK")

    private fun median(values: List<Long>): Long = values.sorted()[values.size / 2]

    @Test
    fun decodeResources() {
        val apk = File(apkPath ?: return)
        val times = mutableListOf<Long>()
        repeat(8) { run ->
            val root = createTempDirectory("decode-bench").toFile()
            val coder = ArsclibResourceCoder(root.resolve("working").apply { mkdirs() }, apk)
            val start = System.nanoTime()
            coder.decodeResources()
            val elapsed = System.nanoTime() - start
            coder.close()
            root.deleteRecursively()
            if (run >= 1) times += elapsed // one warm-up run
            println("BENCH decodeResources run=$run ms=${elapsed / 1_000_000.0}")
        }
        println("BENCH decodeResources median_ms=${median(times) / 1_000_000.0} min_ms=${times.min() / 1_000_000.0} max_ms=${times.max() / 1_000_000.0}")
        PhaseProfiler.report()
    }

    /** Time to close the lazy files while writes are still queued and in flight. */
    @Test
    fun closeWithWritesPending() {
        val apk = File(apkPath ?: return)
        val names = java.util.zip.ZipFile(apk).use { zip ->
            zip.entries().toList().map { it.name }
                .filter { it.startsWith("res/") && !it.endsWith(".xml") && !it.endsWith("/") }
        }
        val times = mutableListOf<Long>()
        repeat(8) { run ->
            val root = createTempDirectory("close-bench").toFile()
            val lazy = LazyResFiles(apk)
            names.forEach { lazy.defer(File(root, "work/resources/0/$it"), it) }
            lazy.start()
            Thread.sleep(50)
            val start = System.nanoTime()
            lazy.close()
            val elapsed = System.nanoTime() - start
            root.deleteRecursively()
            if (run >= 1) times += elapsed
            println("BENCH close run=$run ms=${elapsed / 1_000_000.0}")
        }
        println("BENCH close median_ms=${median(times) / 1_000_000.0} min_ms=${times.min() / 1_000_000.0} max_ms=${times.max() / 1_000_000.0}")
    }
}
