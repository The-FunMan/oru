/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.util

import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.patch.BytecodePatchContext
import io.mockk.mockk
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.MessageDigest
import java.util.Random
import kotlin.test.Test

/**
 * Timing and output digest of the class index on one APK. Skipped unless the environment variable
 * `ORU_BENCH_APK` names an APK. Results are written to the file named by `ORU_BENCH_OUT`, if set.
 *
 * Measures [BytecodePatchContext.decodeDexFiles] and a fixed set of [PatchClasses.candidateClasses] calls
 * on lists taken from the index. The digest covers every candidate result in order, so two code versions
 * with the same digest return the same classes in the same order.
 */
internal class ClassIndexBenchmark {
    @TempDir
    lateinit var temporaryDirectory: File

    @Test
    fun `class index timings and candidate digest`() {
        val apk = System.getenv("ORU_BENCH_APK")?.let(::File)
        assumeTrue(apk != null && apk.isFile, "ORU_BENCH_APK is not set")

        val decodeMillis = (0 until WARMUP_RUNS + TIMED_RUNS).map { run ->
            val context = newContext(apk!!, run)
            val start = System.nanoTime()
            context.decodeDexFiles()
            val elapsed = System.nanoTime() - start
            context.close()
            elapsed / 1_000_000.0
        }.drop(WARMUP_RUNS)

        val context = newContext(apk!!, -1)
        context.decodeDexFiles()
        val queries = candidateQueries(context.patchClasses)

        val digest = MessageDigest.getInstance("SHA-256")
        queries.forEach { query ->
            digest.update(context.patchClasses.candidateClasses(query).classes.map { it.ordinal }.toString().toByteArray())
        }
        val digestHex = digest.digest().joinToString("") { "%02x".format(it) }

        val candidateMillis = (0 until WARMUP_RUNS + TIMED_RUNS).map {
            val start = System.nanoTime()
            var sink = 0
            queries.forEach { query -> sink += context.patchClasses.candidateClasses(query).classes.size }
            check(sink >= 0)
            (System.nanoTime() - start) / 1_000_000.0
        }.drop(WARMUP_RUNS)
        val classCount = context.patchClasses.classMap.size
        context.close()

        report(
            "classes: $classCount",
            "decodeDexFiles ms: ${summary(decodeMillis)}",
            "candidateClasses ms for ${queries.size} queries: ${summary(candidateMillis)}",
            "candidate digest: $digestHex",
        )
    }

    private fun newContext(apk: File, run: Int): BytecodePatchContext {
        val config = PatcherConfig(apk, temporaryDirectory.resolve("run$run")).apply {
            initializeTemporaryFilesDirectories()
        }
        return BytecodePatchContext(config, mockk(relaxed = true))
    }

    /**
     * Fixed queries: pairs and triples of lists from the string constants and the referenced types.
     */
    private fun candidateQueries(patchClasses: PatchClasses): List<List<List<PatchClasses.ClassDefWrapper>>> {
        val stringLists = patchClasses.getClassesByReferenceMap().toSortedMap().values
            .filter { it.size in 1..MAXIMUM_POOL_LIST_SIZE }
            .filterIndexed { index, _ -> index % STRING_SAMPLE_STEP == 0 }
        val typeLists = patchClasses.classMap.keys.sorted()
            .filterIndexed { index, _ -> index % TYPE_SAMPLE_STEP == 0 }
            .map { patchClasses.getIndexedClassesReferencingType(it) }
            .filter { it.isNotEmpty() && it.size <= MAXIMUM_POOL_LIST_SIZE }
        val pool = stringLists + typeLists

        val random = Random(QUERY_SEED)
        return List(QUERY_COUNT) { index ->
            List(if (index % 4 == 0) 3 else 2) { pool[random.nextInt(pool.size)] }
        }
    }

    private fun summary(millis: List<Double>): String {
        val sorted = millis.sorted()
        val median = sorted[sorted.size / 2]
        return "median %.2f min %.2f max %.2f (n=%d)".format(median, sorted.first(), sorted.last(), sorted.size)
    }

    private fun report(vararg lines: String) {
        val text = lines.joinToString("\n", postfix = "\n")
        System.getenv("ORU_BENCH_OUT")?.let { File(it).appendText(text) }
        println(text)
    }

    private companion object {
        const val WARMUP_RUNS = 2
        const val TIMED_RUNS = 7
        const val QUERY_COUNT = 4000
        const val QUERY_SEED = 42L
        const val MAXIMUM_POOL_LIST_SIZE = 5000
        const val STRING_SAMPLE_STEP = 3
        const val TYPE_SAMPLE_STEP = 40
    }
}
