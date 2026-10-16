/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.resource.processor

import app.morphe.patcher.resource.PublicXmlManager
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.system.measureNanoTime

/**
 * Allocation and time of the id and sanitize decisions over the XML files of a decoded resource tree. Runs only with
 * -Poru.bench=true and ORU_DECODED_RES set. The "legacy" rows read each file whole, as the code did before the
 * streaming change; the "stream" rows are the code under test. Allocation is the bytes the test thread allocated.
 */
internal class IdAndSanitizeScanBenchmark {
    @TempDir
    lateinit var tempDir: File

    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    private val runs = 7

    @Test
    fun `benchmark id and sanitize decisions on a decoded tree`() {
        val tree = System.getenv("ORU_DECODED_RES")
        assumeTrue(tree != null, "ORU_DECODED_RES is not set")
        val files = File(tree!!).walkTopDown().filter { it.isFile && it.extension == "xml" }.toList()
        val totalBytes = files.sumOf { it.length() }
        val processor = ResourceIdProcessor(
            get = { tempDir.resolve(it) },
            publicIdManager = PublicXmlManager(
                tempDir.resolve("public.xml").also {
                    it.writeText("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<resources package=\"com.example.app\" id=\"0x7f\">\n</resources>")
                },
            ),
            modifiedResResources = emptySet(),
        )
        val buffer = ByteArray(64 * 1024)

        // Each pass is run once for warm-up and then `runs` times for time. Allocation is the same on every pass.
        val rows = listOf(
            "id legacy whole-file" to { files.count { legacyCanHave(it.readBytes()) } },
            "id legacy byte scan only" to { files.count { legacyIdByteHit(it.readBytes()) } },
            "id stream byte scan only" to { files.count { file -> file.inputStream().use { streamMayHaveIdOrThemeAttrDeclarations(it, buffer) } } },
            "id stream" to { files.count { processor.canHaveIdOrThemeAttrDeclarations(it) } },
            "sanitize legacy whole-file" to { files.count { legacyMayNeedSanitize(it.readBytes()) } },
            "sanitize stream" to { files.count { file -> file.inputStream().use { mayNeedSanitize(it, buffer) } } },
        )
        println("files ${files.size}, ${totalBytes} bytes")
        for ((label, pass) in rows) {
            pass()
            val times = DoubleArray(runs) { measureNanoTime { pass() } / 1_000_000.0 }.sorted()
            val hits = pass()
            val allocated = allocatedBy(pass)
            println(
                "%-28s hits %5d  allocated %12d bytes  median %8.2f ms  min %8.2f  max %8.2f".format(
                    label, hits, allocated, times[runs / 2], times.first(), times.last(),
                ),
            )
        }
    }

    private fun allocatedBy(block: () -> Int): Long {
        val id = Thread.currentThread().id
        val before = threads.getThreadAllocatedBytes(id)
        block()
        return threads.getThreadAllocatedBytes(id) - before
    }
}
