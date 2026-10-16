package app.morphe.patcher.resource.coder

import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class LazyResFilesTest {
    private lateinit var dir: File
    private lateinit var apk: File
    private val big = ByteArray(200_000) { (it % 251).toByte() }
    private val closeables = mutableListOf<LazyResFiles>()

    @BeforeTest
    fun setUp() {
        dir = kotlin.io.path.createTempDirectory("lazy-res").toFile()
        apk = File(dir, "app.apk")
        ZipOutputStream(apk.outputStream()).use { zip ->
            mapOf(
                "res/a.png" to byteArrayOf(1, 2, 3),
                "res/b.png" to byteArrayOf(4, 5),
                "res/c.png" to big,
            ).forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    @AfterTest
    fun tearDown() {
        closeables.forEach { it.close() }
        dir.deleteRecursively()
    }

    /** Without workers, so only [LazyResFiles.awaitFor] and [LazyResFiles.awaitAll] write. */
    private fun files(workers: Int = 0, onWritten: (File) -> Unit = {}) =
        LazyResFiles(apk, workers, onWritten).also {
            closeables += it
            it.defer(File(dir, "work/res/drawable/a.png"), "res/a.png")
            it.defer(File(dir, "work/res/drawable/b.png"), "res/b.png")
            it.defer(File(dir, "work/res/raw/c.png"), "res/c.png")
        }

    @Test
    fun `nothing is written until asked for`() {
        val lazy = files()
        assertFalse(File(dir, "work/res").exists())
        assertEquals(3, lazy.queued)
    }

    @Test
    fun `a file is written once with the content of its archive entry`() {
        val written = mutableListOf<String>()
        val lazy = files(onWritten = { written += it.name })
        val target = File(dir, "work/res/drawable/a.png")
        lazy.awaitFor(target)
        assertContentEquals(byteArrayOf(1, 2, 3), target.readBytes())
        assertEquals(2, lazy.queued)

        lazy.awaitFor(target)
        assertEquals(listOf("a.png"), written)
    }

    @Test
    fun `a directory writes everything below it and nothing else`() {
        val lazy = files()
        lazy.awaitFor(File(dir, "work/res/drawable"))
        assertTrue(File(dir, "work/res/drawable/a.png").exists())
        assertTrue(File(dir, "work/res/drawable/b.png").exists())
        assertFalse(File(dir, "work/res/raw/c.png").exists())
        assertEquals(1, lazy.queued)
    }

    @Test
    fun `a directory name does not match a sibling that shares its prefix`() {
        val lazy = files()
        lazy.defer(File(dir, "work/res/drawable-hdpi/d.png"), "res/a.png")
        lazy.awaitFor(File(dir, "work/res/drawable"))
        assertFalse(File(dir, "work/res/drawable-hdpi/d.png").exists())
    }

    @Test
    fun `await all writes large files intact`() {
        val lazy = files()
        lazy.awaitAll()
        assertEquals(0, lazy.queued)
        assertContentEquals(big, File(dir, "work/res/raw/c.png").readBytes())
        assertEquals(3, lazy.writtenCount)
    }

    @Test
    fun `a file a patch created first is not overwritten`() {
        val lazy = files()
        val target = File(dir, "work/res/drawable/a.png")
        target.parentFile.mkdirs()
        target.writeBytes(byteArrayOf(9))
        lazy.awaitFor(target)
        assertContentEquals(byteArrayOf(9), target.readBytes())
        assertEquals(0, lazy.writtenCount)
    }

    @Test
    fun `background workers write everything and awaiting sees it complete`() {
        val lazy = files(workers = 3)
        lazy.start()
        lazy.awaitFor(File(dir, "work/res"))
        assertContentEquals(byteArrayOf(1, 2, 3), File(dir, "work/res/drawable/a.png").readBytes())
        assertContentEquals(big, File(dir, "work/res/raw/c.png").readBytes())
    }

    @Test
    fun `many files with several workers and concurrent awaits all arrive intact`() {
        val lazy = LazyResFiles(apk, 3).also { closeables += it }
        repeat(500) { lazy.defer(File(dir, "work/res/many/f$it.png"), "res/c.png") }
        lazy.start()
        val threads = (0 until 4).map { t ->
            Thread { lazy.awaitFor(File(dir, "work/res/many")) ; if (t == 0) lazy.awaitAll() }.also { it.start() }
        }
        threads.forEach { it.join() }
        assertEquals(500, lazy.writtenCount)
        assertTrue((0 until 500).all { File(dir, "work/res/many/f$it.png").length() == big.size.toLong() })
    }

    @Test
    fun `a file whose archive entry is missing is not left behind`() {
        val lazy = LazyResFiles(apk, 0).also { closeables += it }
        val target = File(dir, "work/res/drawable/gone.png")
        lazy.defer(target, "res/gone.png")
        assertFailsWith<IOException> { lazy.awaitFor(target) }
        assertFalse(target.exists())
    }

    @Test
    fun `a missing archive entry is reported`() {
        val lazy = files()
        lazy.defer(File(dir, "work/res/x.png"), "res/missing.png")
        assertFailsWith<IOException> { lazy.awaitFor(File(dir, "work/res/x.png")) }
    }

    @Test
    fun `writer priority is minimum by default and can be set to normal`() {
        assertEquals(Thread.MIN_PRIORITY, rawWriterPriority(null))
        assertEquals(Thread.MIN_PRIORITY, rawWriterPriority("min"))
        assertEquals(Thread.NORM_PRIORITY, rawWriterPriority("norm"))
        assertFailsWith<IllegalArgumentException> { rawWriterPriority("high") }
    }
}
