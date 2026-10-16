/*
 * Copyright 2026 Morphe.
 * https://github.com/morpheapp/morphe-patcher
 */

package app.morphe.patcher.util

import app.morphe.patcher.resource.coder.ArsclibResourceCoder
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import kotlin.system.measureNanoTime

/**
 * Parse and transform benchmark over the XML files of a decoded stock APK. Runs only when ORU_TEST_APK
 * (the APK) and ORU_BENCH_DIR (a scratch directory) are set. Writes the timings to
 * ORU_BENCH_DIR/results-ORU_BENCH_LABEL.txt and the transformed files to ORU_BENCH_DIR/out-ORU_BENCH_LABEL,
 * so two code versions can be compared byte for byte with diff -r.
 */
internal class DocumentFactoryBenchmark {
    private val apk = System.getenv("ORU_TEST_APK")
    private val benchDir = System.getenv("ORU_BENCH_DIR")
    private val label = System.getenv("ORU_BENCH_LABEL") ?: "run"

    private val warmUps = 2
    private val runs = 7

    @Test
    fun `benchmark document parse and transform on a decoded apk`() {
        assumeTrue(apk != null && benchDir != null, "ORU_TEST_APK and ORU_BENCH_DIR are not set")
        val root = File(benchDir!!).apply { mkdirs() }
        val decoded = root.resolve("decoded-$label").apply { deleteRecursively(); mkdirs() }
        ArsclibResourceCoder(decoded, File(apk!!)).use { it.decodeResources() }

        val names = decoded.walkTopDown()
            .filter { it.isFile && it.extension == "xml" }
            .map { it.relativeTo(decoded).path }
            .sorted()
            .toList()
        val work = root.resolve("work-$label")

        val builderCreations = AtomicInteger()
        val transformerCreations = AtomicInteger()
        val builderFactory = XmlFactories.newDocumentBuilderFactory
        val transformerFactory = XmlFactories.newTransformerFactory
        XmlFactories.newDocumentBuilderFactory = {
            builderCreations.incrementAndGet()
            builderFactory()
        }
        XmlFactories.newTransformerFactory = {
            transformerCreations.incrementAndGet()
            transformerFactory()
        }

        val report = StringBuilder()
        val runMillis = mutableListOf<Double>()
        val perRunCounts = mutableListOf<String>()
        try {
            repeat(warmUps + runs) { run ->
                stage(decoded, work, names)
                val builderBefore = builderCreations.get()
                val transformerBefore = transformerCreations.get()
                val elapsed = measureNanoTime { transform(work, names) }
                if (run >= warmUps) {
                    runMillis += elapsed / 1_000_000.0
                    perRunCounts += "documents ${names.size}, builder factories ${builderCreations.get() - builderBefore}, " +
                        "transformer factories ${transformerCreations.get() - transformerBefore}"
                }
            }
        } finally {
            XmlFactories.newDocumentBuilderFactory = builderFactory
            XmlFactories.newTransformerFactory = transformerFactory
        }

        // Keep one transformed tree for the byte comparison, outside the timed runs.
        val out = root.resolve("out-$label").apply { deleteRecursively() }
        stage(decoded, out, names)
        transform(out, names)

        report.appendLine("xml files: ${names.size}")
        report.appendLine("parse and transform, whole set (s, median of $runs, min..max):")
        report.appendLine(format(runMillis.map { it / 1000.0 }, decimals = 3))
        report.appendLine("per run counts: ${perRunCounts.first()}")

        val builderMillis = sample { XmlFactories.newDocumentBuilder() }
        val builderOnlyMillis = sample { DocumentBuilderFactory.newInstance().newDocumentBuilder() }
        val transformerMillis = sample { XmlFactories.newTransformer() }
        val transformerOnlyMillis = sample { TransformerFactory.newInstance().newTransformer() }
        report.appendLine("XmlFactories.newDocumentBuilder() (ms, median of $runs, min..max):")
        report.appendLine(format(builderMillis, decimals = 4))
        report.appendLine("DocumentBuilderFactory.newInstance().newDocumentBuilder() (ms, median of $runs, min..max):")
        report.appendLine(format(builderOnlyMillis, decimals = 4))
        report.appendLine("XmlFactories.newTransformer() (ms, median of $runs, min..max):")
        report.appendLine(format(transformerMillis, decimals = 4))
        report.appendLine("TransformerFactory.newInstance().newTransformer() (ms, median of $runs, min..max):")
        report.appendLine(format(transformerOnlyMillis, decimals = 4))

        root.resolve("results-$label.txt").writeText(report.toString())
    }

    private fun stage(source: File, target: File, names: List<String>) {
        target.deleteRecursively()
        names.forEach { name ->
            val destination = target.resolve(name)
            destination.parentFile.mkdirs()
            source.resolve(name).copyTo(destination)
        }
    }

    private fun transform(target: File, names: List<String>) {
        names.forEach { name -> Document(target.resolve(name)).close() }
    }

    private fun sample(block: () -> Unit): List<Double> {
        repeat(20) { block() }
        return (0 until runs).map { measureNanoTime { block() } / 1_000_000.0 }
    }

    private fun format(values: List<Double>, decimals: Int): String {
        val sorted = values.sorted()
        val median = sorted[sorted.size / 2]
        return "  median %.${decimals}f, min %.${decimals}f, max %.${decimals}f".format(median, sorted.first(), sorted.last())
    }
}
