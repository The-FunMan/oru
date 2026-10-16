/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.resource.processor

import app.morphe.patcher.resource.coder.ArsclibResourceCoder
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.system.measureNanoTime

/**
 * Manual benchmark of the strings processors on a decoded stock APK. Runs only when ORU_TEST_APK (the APK)
 * and ORU_BENCH_DIR (a scratch directory) are set, and writes its numbers to
 * ORU_BENCH_DIR/strings-results-ORU_BENCH_LABEL.txt.
 */
internal class StringsXmlProcessorBenchmark {
    private val apk = System.getenv("ORU_TEST_APK")
    private val benchDir = System.getenv("ORU_BENCH_DIR")
    private val label = System.getenv("ORU_BENCH_LABEL") ?: "run"

    private val warmUps = 2
    private val runs = 7

    @Test
    fun `benchmark strings processors on a decoded apk`() {
        assumeTrue(apk != null && benchDir != null, "ORU_TEST_APK and ORU_BENCH_DIR are not set")
        val root = File(benchDir!!).apply { mkdirs() }
        val decoded = root.resolve("strings-decoded-$label").apply { deleteRecursively(); mkdirs() }

        val packageDirectories = ArsclibResourceCoder(decoded, File(apk!!)).use { coder ->
            coder.decodeResources()
            coder.packageDirectories.toMap()
        }

        // The decoder has already escaped the strings, so the escape input is rebuilt by unescaping a copy.
        val copyRoot = root.resolve("strings-copy-$label")
        val copyDirectories = packageDirectories.keys.associateWith { copyRoot.resolve(it) }
        val sourceFiles = packageDirectories.flatMap { (pkg, rootDir) ->
            rootDir.resolve("res").listFiles { it.isDirectory }.orEmpty().mapNotNull { dir ->
                dir.resolve("strings.xml").takeIf { it.isFile }?.let { Triple(pkg, rootDir, it) }
            }
        }
        val getCopy: (String, String) -> File = { path, pkg -> copyRoot.resolve(pkg).resolve(path) }

        val prepare = {
            copyRoot.deleteRecursively()
            sourceFiles.forEach { (pkg, rootDir, file) ->
                val target = copyRoot.resolve(pkg).resolve(file.relativeTo(rootDir))
                target.parentFile.mkdirs()
                file.copyTo(target)
            }
            StringsXmlUnEscapeProcessor(getCopy, copyDirectories).process()
        }

        // Count files whose text the escape changes, on one prepared copy.
        prepare()
        val before = sourceFiles.associate { (pkg, rootDir, file) ->
            copyRoot.resolve(pkg).resolve(file.relativeTo(rootDir)) to file.readBytes()
        }
        StringsXmlEscapeProcessor(getCopy, copyDirectories).process()
        val escapeChanged = before.count { (file, bytes) -> !file.readBytes().contentEquals(bytes) }

        val sanitizeTimes = mutableListOf<Double>()
        val escapeTimes = mutableListOf<Double>()
        repeat(warmUps + runs) { index ->
            prepare()
            val sanitizeMs = measureNanoTime {
                StringsXmlSanitizeProcessor(getCopy, copyDirectories).process()
            } / 1_000_000.0
            prepare()
            val escapeMs = measureNanoTime {
                StringsXmlEscapeProcessor(getCopy, copyDirectories).process()
            } / 1_000_000.0
            if (index >= warmUps) {
                sanitizeTimes += sanitizeMs
                escapeTimes += escapeMs
            }
        }

        val report = StringBuilder()
        report.appendLine("strings.xml files: ${sourceFiles.size}")
        report.appendLine("files changed by escape: $escapeChanged, unchanged: ${sourceFiles.size - escapeChanged}")
        report.appendLine("sanitize processor (ms, median of $runs, min..max):")
        report.appendLine(format(sanitizeTimes))
        report.appendLine("escape processor (ms, median of $runs, min..max):")
        report.appendLine(format(escapeTimes))
        val totals = List(runs) { sanitizeTimes[it] + escapeTimes[it] }
        report.appendLine("sanitize plus escape per run (ms, median of $runs, min..max):")
        report.appendLine(format(totals))
        root.resolve("strings-results-$label.txt").writeText(report.toString())
    }

    private fun format(times: List<Double>): String {
        val sorted = times.sorted()
        return "  median %.1f  min %.1f  max %.1f  runs %s".format(
            sorted[sorted.size / 2], sorted.first(), sorted.last(), times.joinToString(" ") { "%.1f".format(it) },
        )
    }
}
