/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.dex

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.system.measureNanoTime

/**
 * Component benchmark for the dex strip step of STRIP_FAST. Runs only when ORU_DEX_BENCH_APK
 * is set (path to an APK read only), so the default test task skips it.
 *
 * Environment:
 * - ORU_DEX_BENCH_APK: input APK.
 * - ORU_DEX_BENCH_OUT: scratch directory for working copies and results.
 * - ORU_DEX_BENCH_LABEL: name of the run, used in the result file names.
 * - ORU_DEX_BENCH_ITER: measured iterations (default 7), after 3 warm-up iterations.
 *
 * Writes `<out>/results-<label>.txt` (timings, in ms) and `<out>/hashes-<label>.txt`
 * (SHA-256 of every stripped file after the first sequential iteration, for byte comparison).
 */
internal class DexStripBenchmark {

    @Test
    fun stripBenchmark() {
        val apkPath = System.getenv("ORU_DEX_BENCH_APK")
        assumeTrue(apkPath != null, "set ORU_DEX_BENCH_APK to run the strip benchmark")
        val outDir = File(checkNotNull(System.getenv("ORU_DEX_BENCH_OUT")) { "ORU_DEX_BENCH_OUT is required" })
            .apply { mkdirs() }
        val label = System.getenv("ORU_DEX_BENCH_LABEL") ?: "run"
        val iterations = System.getenv("ORU_DEX_BENCH_ITER")?.toInt() ?: 7
        val warmup = 3

        val apk = File(apkPath!!)
        val originals = DexReadWrite.extractDexEntries(apk, File(outDir, "orig"))
            .sortedBy { it.name }

        // Every 20th class descriptor of the whole container, in sorted order: a fixed, realistic-size
        // selection spread over all DEX files.
        val descriptors = DexReadWrite.readMultidexFileFromZip(apk, File(outDir, "parse"))
            .use { result -> result.classDescriptorsByEntry.value.values.flatten().distinct().sorted() }
            .filterIndexed { index, _ -> index % 20 == 0 }
            .toSet()

        val work = File(outDir, "work")
        val pool = Executors.newFixedThreadPool(
            minOf(Runtime.getRuntime().availableProcessors(), originals.size).coerceAtLeast(1),
        )

        fun prepare() {
            work.deleteRecursively()
            work.mkdirs()
            originals.forEach { it.copyTo(File(work, it.name)) }
        }

        fun stripSequential(): Long {
            prepare()
            return measureNanoTime {
                work.listFiles { f -> f.isFile }!!.sortedBy { it.name }.forEach { file ->
                    MappedFile.mapReadWrite(file).use { DexStripper.stripInPlace(it, descriptors) }
                }
            }
        }

        fun stripParallel(): Long {
            prepare()
            val files = work.listFiles { f -> f.isFile }!!.sortedBy { it.name }
            return measureNanoTime {
                files.map { file ->
                    pool.submit(Callable {
                        MappedFile.mapReadWrite(file).use { DexStripper.stripInPlace(it, descriptors) }
                    })
                }.forEach { it.get() }
            }
        }

        // Byte check: stripped output of one sequential pass.
        stripSequential()
        val hashes = work.listFiles { f -> f.isFile }!!.sortedBy { it.name }
            .joinToString("\n") { "${it.name} ${sha256(it)}" }
        File(outDir, "hashes-$label.txt").writeText(hashes + "\n")

        stripParallel()
        val parallelHashes = work.listFiles { f -> f.isFile }!!.sortedBy { it.name }
            .joinToString("\n") { "${it.name} ${sha256(it)}" }
        check(parallelHashes == hashes) { "parallel strip output differs from sequential output" }

        val results = StringBuilder()
        results.append("label=$label cores=${Runtime.getRuntime().availableProcessors()} descriptors=${descriptors.size}\n")
        for ((name, run) in listOf<Pair<String, () -> Long>>("sequential" to ::stripSequential, "parallel" to ::stripParallel)) {
            repeat(warmup) { run() }
            val times = List(iterations) { run() / 1_000_000.0 }
            val sorted = times.sorted()
            results.append(
                "$name median=%.2f min=%.2f max=%.2f ms n=$iterations\n".format(
                    sorted[sorted.size / 2], sorted.first(), sorted.last(),
                ),
            )
        }
        pool.shutdown()
        File(outDir, "results-$label.txt").writeText(results.toString())
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
