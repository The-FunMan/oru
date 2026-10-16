package app.morphe.patcher.resource.coder

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.lang.management.ManagementFactory
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Byte identity of the raw writer across entry sizes around its copy buffer, and what one write allocates. */
internal class LazyResFilesCopyTest {
    private lateinit var dir: File
    private lateinit var apk: File
    private val closeables = mutableListOf<LazyResFiles>()

    /** Sizes around the copy buffer (64 KB), and a few that span several reads. */
    private val sizes = listOf(0, 1, 3, 4_095, 4_096, 65_535, 65_536, 65_537, 131_072, 200_000, 1_000_003)
    private val contents: Map<String, ByteArray> = sizes.withIndex().associate { (i, size) ->
        "res/size$size.bin" to Random(i).nextBytes(size)
    }

    @BeforeTest
    fun setUp() {
        dir = kotlin.io.path.createTempDirectory("lazy-copy").toFile()
        apk = File(dir, "app.apk")
        writeApk(apk, contents)
    }

    @AfterTest
    fun tearDown() {
        closeables.forEach { it.close() }
        dir.deleteRecursively()
    }

    private fun writeApk(file: File, entries: Map<String, ByteArray>, stored: Boolean = false) {
        ZipOutputStream(file.outputStream()).use { zip ->
            if (stored) zip.setMethod(ZipOutputStream.STORED)
            entries.forEach { (name, bytes) ->
                val entry = ZipEntry(name)
                if (stored) {
                    entry.size = bytes.size.toLong()
                    entry.compressedSize = bytes.size.toLong()
                    entry.crc = CRC32().apply { update(bytes) }.value
                }
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    /** Bytes the writer path allocates per file on the calling thread, for [count] files of one entry. */
    // Thread.id is deprecated in newer JDKs; kept because this module targets JVM 11 and the value is the same.
    @Suppress("DEPRECATION")
    private fun allocatedPerFile(stored: Boolean, count: Int = 2_000): Long {
        val bean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        assumeTrue(bean != null && bean.isThreadAllocatedMemorySupported)
        bean!!.isThreadAllocatedMemoryEnabled = true
        val entry = "res/twenty.bin"
        val payload = Random(7).nextBytes(20 * 1024)
        val archive = File(dir, "twenty-$stored.apk").also { writeApk(it, mapOf(entry to payload), stored) }
        val lazy = LazyResFiles(archive, workerCount = 0).also { closeables += it }
        repeat(count) { lazy.defer(File(dir, "alloc-$stored/f$it.bin"), entry) }

        val threadId = Thread.currentThread().id
        val before = bean.getThreadAllocatedBytes(threadId)
        lazy.awaitAll()
        val allocated = bean.getThreadAllocatedBytes(threadId) - before

        assertEquals(count, lazy.writtenCount)
        assertContentEquals(payload, File(dir, "alloc-$stored/f${count - 1}.bin").readBytes())
        val perFile = allocated / count
        println("ALLOC copy-path stored=$stored bytesPerFile=$perFile total=$allocated files=$count entryBytes=${payload.size}")
        return perFile
    }

    @Test
    fun `every size writes the same bytes as its archive entry`() {
        val lazy = LazyResFiles(apk, workerCount = 0).also { closeables += it }
        contents.keys.forEach { name -> lazy.defer(File(dir, "out/$name"), name) }
        lazy.awaitAll()
        assertEquals(contents.size, lazy.writtenCount)
        contents.forEach { (name, bytes) -> assertContentEquals(bytes, File(dir, "out/$name").readBytes(), name) }
    }

    @Test
    fun `files written concurrently from background workers and caller threads keep their bytes`() {
        val lazy = LazyResFiles(apk, workerCount = 3).also { closeables += it }
        val targets = (0 until 300).map { i ->
            val name = contents.keys.elementAt(i % contents.size)
            File(dir, "out/t$i/${name.substringAfterLast('/')}") to name
        }
        targets.forEach { (target, name) -> lazy.defer(target, name) }
        lazy.start()
        val callers = (0 until 4).map { t ->
            Thread {
                // Each caller awaits its own slice, so writes race on the caller threads as well as the workers
                targets.filterIndexed { i, _ -> i % 4 == t }.forEach { (target, _) -> lazy.awaitFor(target) }
            }.also { it.start() }
        }
        callers.forEach { it.join() }
        lazy.awaitAll()
        assertEquals(targets.size, lazy.writtenCount)
        targets.forEach { (target, name) -> assertContentEquals(contents.getValue(name), target.readBytes(), target.path) }
    }

    @Test
    fun `a write on the caller thread allocates far less than a copy buffer per file`() {
        val stored = allocatedPerFile(stored = true)
        allocatedPerFile(stored = false) // deflated entries also carry the inflater's input buffer, sized to the entry
        assertTrue(stored < 16 * 1024, "a write allocated $stored bytes, expected no copy buffer per file")
    }
}
