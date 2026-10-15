/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.resource.processor

import app.morphe.patcher.resource.CanonicalFileOrder
import app.morphe.patcher.resource.PublicXmlManager
import app.morphe.patcher.resource.fileResourceTypes
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.lang.management.ManagementFactory

/**
 * Times and counts the allocations of the file-based public id loop of [ResourceIdProcessor] on a synthetic tree of
 * about 17,000 files in 156 directories. Run with `-Poru.bench=true --tests "*ResourceIdLoopBenchmark*"`; the
 * numbers are printed to the test output. Set ORU_DECODED_RES to a copy of a decoded `res` directory to also run it
 * there. The processor writes ids.xml into that tree, so it is only read once per run.
 */
internal class ResourceIdLoopBenchmark {
    @TempDir
    lateinit var tempDir: File

    private val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    @Test
    fun `benchmark the file-based public id loop`() {
        val root = tempDir.resolve("synthetic").apply { mkdirs() }
        val res = SyntheticResTree.write(root, filesPerDirectory = 110)
        println("synthetic tree: ${res.walkTopDown().count { it.isFile }} files, ${res.listFiles()!!.count { it.isDirectory }} directories")
        report(root, res, "synthetic")

        System.getenv("ORU_DECODED_RES")?.let { decoded ->
            val decodedRes = File(decoded)
            report(decodedRes.parentFile, decodedRes, "decoded")
        }
    }

    private fun report(root: File, res: File, label: String) {
        val publicXml = root.resolve("res/values/public.xml")
        val publicXmlBytes = publicXml.readBytes()
        val managers = { PublicXmlManager(publicXml.also { it.writeBytes(publicXmlBytes) }) }
        val processor = { manager: PublicXmlManager ->
            ResourceIdProcessor(get = { root.resolve(it) }, publicIdManager = manager, modifiedResResources = emptySet())
        }

        // What the loop lists and stats, with no id work.
        val listed = listFileResources(res)
        println("[$label] file resources visited by the loop: ${listed.size}")
        measure("[$label] list and stat only") { listFileResources(res) }

        // What createPublicId costs for the same files, with the list already built.
        measure(
            label = "[$label] createPublicId only",
            setup = { managers() },
        ) { manager -> listed.forEach { (type, file) -> manager.createPublicId(type, file.nameWithoutExtension) } }

        measure(
            label = "[$label] ResourceIdProcessor.process",
            setup = { processor(managers()) },
        ) { it.process() }
    }

    /** The loop as it was: every file-based directory listed, each file stat'd and sorted. */
    private fun listFileResources(res: File): List<Pair<String, File>> {
        val resDirectories = CanonicalFileOrder.sort(res.listFiles()!!.filter { it.isDirectory })
        val result = mutableListOf<Pair<String, File>>()
        for (type in fileResourceTypes) {
            for (dir in resDirectories.filter { it.name.startsWith(type) }) {
                val files = CanonicalFileOrder.sort(dir.listFiles()!!.filter { it.isFile })
                files.forEach { result += type to it }
            }
        }
        return result
    }

    private fun <T> measure(label: String, setup: () -> T, body: (T) -> Unit) {
        repeat(WARM_UPS) { body(setup()) }
        val nanos = mutableListOf<Long>()
        val bytes = mutableListOf<Long>()
        repeat(RUNS) {
            val state = setup()
            val allocatedBefore = bean.currentThreadAllocatedBytes
            val start = System.nanoTime()
            body(state)
            nanos += System.nanoTime() - start
            bytes += bean.currentThreadAllocatedBytes - allocatedBefore
        }
        println(
            "$label: median ${nanos.sorted()[RUNS / 2] / 1_000_000.0} ms " +
                    "(min ${nanos.min() / 1_000_000.0}, max ${nanos.max() / 1_000_000.0}), " +
                    "allocated median ${bytes.sorted()[RUNS / 2] / 1024} KiB",
        )
    }

    private fun measure(label: String, body: () -> Unit) = measure(label, setup = {}) { body() }

    private companion object {
        const val WARM_UPS = 2
        const val RUNS = 7
    }
}
