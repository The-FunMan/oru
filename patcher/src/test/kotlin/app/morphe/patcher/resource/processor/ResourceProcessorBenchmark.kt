/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.resource.processor

import app.morphe.patcher.resource.PublicXmlManager
import app.morphe.patcher.resource.coder.ArsclibResourceCoder
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.system.measureNanoTime

/**
 * Manual benchmark of the resource processors on a decoded stock APK. Runs only when
 * ORU_TEST_APK (the APK) and ORU_BENCH_DIR (a scratch directory) are set, and writes
 * its numbers to ORU_BENCH_DIR/results-ORU_BENCH_LABEL.txt.
 */
internal class ResourceProcessorBenchmark {
    private val apk = System.getenv("ORU_TEST_APK")
    private val benchDir = System.getenv("ORU_BENCH_DIR")
    private val label = System.getenv("ORU_BENCH_LABEL") ?: "run"

    private val warmUps = 2
    private val runs = 7

    private val publicXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<resources package=\"com.example.app\" id=\"0x7f\">\n</resources>"

    @Test
    fun `benchmark resource processors on a decoded apk`() {
        assumeTrue(apk != null && benchDir != null, "ORU_TEST_APK and ORU_BENCH_DIR are not set")
        val root = File(benchDir!!).apply { mkdirs() }
        val decoded = root.resolve("decoded-$label").apply { deleteRecursively(); mkdirs() }

        val report = StringBuilder()
        val packageDirectories = ArsclibResourceCoder(decoded, File(apk!!)).use { coder ->
            coder.decodeResources()
            coder.packageDirectories.toMap()
        }

        val xmlFiles = packageDirectories.values.flatMap { rootDir ->
            rootDir.resolve("res").walkTopDown().filter { it.isFile && it.extension == "xml" }.toList()
        }
        report.appendLine("xml files: ${xmlFiles.size}")

        val processor = ResourceIdProcessor(
            get = { root.resolve(it) },
            publicIdManager = PublicXmlManager(root.resolve("public.xml").also { it.writeText(publicXml) }),
            modifiedResResources = emptySet(),
        )
        // Legacy and new runs alternate, so drift on the machine affects both the same way.
        val legacyTimes = mutableListOf<Double>()
        val prefilterTimes = mutableListOf<Double>()
        repeat(warmUps) {
            legacyCanHaveIdOrThemeAttrDeclarations(xmlFiles)
            xmlFiles.count { processor.canHaveIdOrThemeAttrDeclarations(it) }
        }
        repeat(runs) {
            legacyTimes += measureNanoTime { legacyCanHaveIdOrThemeAttrDeclarations(xmlFiles) } / 1_000_000.0
            prefilterTimes += measureNanoTime { xmlFiles.count { processor.canHaveIdOrThemeAttrDeclarations(it) } } / 1_000_000.0
        }
        report.appendLine("id predicate, legacy text path (ms, median of $runs, min..max):")
        report.appendLine(format(legacyTimes))
        report.appendLine("id predicate, byte prefilter (ms, median of $runs, min..max):")
        report.appendLine(format(prefilterTimes))
        val legacyHits = legacyCanHaveIdOrThemeAttrDeclarations(xmlFiles)
        val prefilterHits = xmlFiles.count { processor.canHaveIdOrThemeAttrDeclarations(it) }
        report.appendLine("hits: legacy $legacyHits, prefilter $prefilterHits")

        // Every label times the escape on the same input: the strings of the "base" tree with the escapes
        // taken off again, which approximates what the decoder hands the escape processor. Each run copies
        // the files and unescapes the copies before the timed escape.
        val inputStrings = stringsFilesIn(root.resolve("decoded-base").takeIf { it.isDirectory } ?: decoded)
        report.appendLine("escape processor over ${inputStrings.size} strings files (ms, median of $runs, min..max):")
        val copyRoot = root.resolve("escape-copy-$label")
        val packages = inputStrings.map { it.first }.distinct()
        val escapeTimes = timed(
            runs,
            before = {
                copyStringsFiles(inputStrings, copyRoot)
                StringsXmlUnEscapeProcessor({ path, pkg -> copyRoot.resolve(pkg).resolve(path) }, packages.associateWith { copyRoot.resolve(it) }).process()
            },
        ) {
            StringsXmlEscapeProcessor(
                { path, pkg -> copyRoot.resolve(pkg).resolve(path) },
                packages.associateWith { copyRoot.resolve(it) },
            ).process()
        }
        report.appendLine(format(escapeTimes))

        root.resolve("results-$label.txt").writeText(report.toString())
    }

    private fun timed(runs: Int, before: () -> Unit = {}, body: () -> Unit): List<Double> {
        repeat(warmUps) { before(); body() }
        return List(runs) {
            before()
            measureNanoTime(body) / 1_000_000.0
        }
    }

    private fun format(times: List<Double>): String {
        val sorted = times.sorted()
        return "  median %.1f  min %.1f  max %.1f  runs %s".format(
            sorted[sorted.size / 2], sorted.first(), sorted.last(), times.joinToString(" ") { "%.1f".format(it) },
        )
    }

    /** The strings files of a decoded tree as (package, package root, file), by the layout of the decoder. */
    private fun stringsFilesIn(tree: File): List<Triple<String, File, File>> =
        tree.resolve("resources").listFiles { it.isDirectory }.orEmpty().flatMap { rootDir ->
            rootDir.resolve("res").listFiles { it.isDirectory }.orEmpty().mapNotNull { dir ->
                dir.resolve("strings.xml").takeIf { it.isFile }?.let { Triple(rootDir.name, rootDir, it) }
            }
        }

    private fun copyStringsFiles(stringsFiles: List<Triple<String, File, File>>, copyRoot: File) {
        copyRoot.deleteRecursively()
        stringsFiles.forEach { (pkg, rootDir, file) ->
            val target = copyRoot.resolve(pkg).resolve(file.relativeTo(rootDir))
            target.parentFile.mkdirs()
            file.copyTo(target)
        }
    }

    /** The predicate as it stood before the byte prefilter. Kept to compare decisions and timings. */
    private fun legacyCanHaveIdOrThemeAttrDeclarations(files: List<File>): Int {
        val regex = Regex("""=\s*["']\?""")
        var hits = 0
        for (file in files) {
            val text = file.readText()
            if ("+id/" in text || "&#" in text || '\u0000' in text || regex.containsMatchIn(text)) hits++
        }
        return hits
    }
}
