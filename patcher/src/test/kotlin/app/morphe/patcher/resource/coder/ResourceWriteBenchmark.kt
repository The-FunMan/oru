package app.morphe.patcher.resource.coder

import com.android.tools.build.apkzlib.zip.ZFile
import java.io.File
import java.util.zip.ZipFile
import kotlin.io.path.createTempDirectory
import kotlin.test.Test

/**
 * Manual benchmark, skipped unless ORU_BENCH_APK names an APK. Prints one line per measurement.
 */
internal class ResourceWriteBenchmark {
    private val apkPath = System.getenv("ORU_BENCH_APK")

    private fun median(values: List<Long>): Long = values.sorted()[values.size / 2]

    @Test
    fun rawWrites() {
        val apk = File(apkPath ?: return)
        val names = ZipFile(apk).use { zip ->
            zip.entries().toList().map { it.name }
                .filter { it.startsWith("res/") && !it.endsWith(".xml") && !it.endsWith("/") }
        }
        val times = mutableListOf<Long>()
        repeat(9) { run ->
            val root = createTempDirectory("raw-bench").toFile()
            val lazy = LazyResFiles(apk, workerCount = 0)
            names.forEach { lazy.defer(File(root, "work/resources/0/$it"), it) }
            val start = System.nanoTime()
            lazy.awaitAll()
            val elapsed = System.nanoTime() - start
            val written = lazy.writtenCount
            lazy.close()
            root.deleteRecursively()
            if (run >= 2) times += elapsed // two warm-up runs
            println("BENCH raw-writes run=$run files=$written ms=${elapsed / 1_000_000.0}")
        }
        println("BENCH raw-writes median_ms=${median(times) / 1_000_000.0} min_ms=${times.min() / 1_000_000.0} max_ms=${times.max() / 1_000_000.0}")
    }

    @Test
    fun zFileReopen() {
        val apk = File(apkPath ?: return)
        val names = ZipFile(apk).use { zip -> zip.entries().toList().map { it.name }.filter { !it.endsWith("/") } }
        val sample = names.filterIndexed { i, _ -> i % 500 == 0 }
        val reopen = mutableListOf<Long>()
        val shared = mutableListOf<Long>()
        repeat(9) { run ->
            // What the coder does per on-demand lookup: open, read one entry, close.
            var start = System.nanoTime()
            sample.forEach { name ->
                ZFile.openReadOnly(apk).use { zFile -> zFile.get(name)?.open()?.use { it.readBytes() } }
            }
            val reopenNs = System.nanoTime() - start
            // One handle for the whole run.
            start = System.nanoTime()
            ZFile.openReadOnly(apk).use { zFile ->
                sample.forEach { name -> zFile.get(name)?.open()?.use { it.readBytes() } }
            }
            val sharedNs = System.nanoTime() - start
            if (run >= 2) {
                reopen += reopenNs
                shared += sharedNs
            }
            println("BENCH zfile run=$run samples=${sample.size} reopen_ms=${reopenNs / 1_000_000.0} shared_ms=${sharedNs / 1_000_000.0}")
        }
        println("BENCH zfile median_reopen_ms=${median(reopen) / 1_000_000.0} median_shared_ms=${median(shared) / 1_000_000.0} " +
                "(open+get+read of ${sample.size} entries, entries=${names.size})")
    }
}
