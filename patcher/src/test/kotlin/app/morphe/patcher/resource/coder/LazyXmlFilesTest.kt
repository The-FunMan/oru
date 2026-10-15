package app.morphe.patcher.resource.coder

import java.io.Closeable
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

internal class LazyXmlFilesTest {
    private lateinit var dir: File
    private lateinit var apk: File
    private val decoded = mutableListOf<String>()
    private val written = mutableListOf<File>()
    private var moduleClosed = false
    private val module = Closeable { moduleClosed = true }
    private val closeables = mutableListOf<LazyXmlFiles>()

    @BeforeTest
    fun setUp() {
        dir = kotlin.io.path.createTempDirectory("lazy-xml").toFile()
        apk = File(dir, "app.apk")
        ZipOutputStream(apk.outputStream()).use { zip ->
            listOf("res/layout/a.xml", "res/layout/b.xml", "res/xml/c.xml").forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(name.toByteArray())
                zip.closeEntry()
            }
        }
    }

    @AfterTest
    fun tearDown() {
        closeables.forEach { it.close() }
        dir.deleteRecursively()
    }

    /** Queues the three entries. The decode records the archive path it was given and writes its bytes to the target. */
    private fun lazy(): LazyXmlFiles = LazyXmlFiles(apk, module, onWritten = { written += it }).also {
        closeables += it
        for (name in listOf("res/layout/a.xml", "res/layout/b.xml", "res/xml/c.xml")) {
            it.defer(File(dir, "work/$name"), name) { input ->
                decoded += name
                File(dir, "work/$name").apply { parentFile.mkdirs() }.writeBytes(input.readBytes())
            }
        }
    }

    @Test
    fun `nothing is decoded until a file is asked for`() {
        lazy()
        assertTrue(decoded.isEmpty())
        assertTrue(written.isEmpty())
    }

    @Test
    fun `awaitFor decodes only the file asked for`() {
        val files = lazy()
        files.awaitFor(File(dir, "work/res/layout/a.xml"))

        assertEquals(listOf("res/layout/a.xml"), decoded)
        assertContentEquals("res/layout/a.xml".toByteArray(), File(dir, "work/res/layout/a.xml").readBytes())
        assertFalse(File(dir, "work/res/xml/c.xml").exists())
    }

    @Test
    fun `awaitFor on a directory decodes everything below it`() {
        lazy().awaitFor(File(dir, "work/res/layout"))

        assertEquals(listOf("res/layout/a.xml", "res/layout/b.xml"), decoded)
        assertFalse(File(dir, "work/res/xml/c.xml").exists())
    }

    @Test
    fun `awaitAll decodes each file once and reports each write`() {
        val files = lazy()
        files.awaitFor(File(dir, "work/res/layout/a.xml"))
        files.awaitAll()
        files.awaitAll()

        assertEquals(listOf("res/layout/a.xml", "res/layout/b.xml", "res/xml/c.xml"), decoded)
        assertEquals(3, written.size)
    }

    @Test
    fun `a written file is back-dated so a later edit shows as a change`() {
        lazy().awaitFor(File(dir, "work/res/xml/c.xml"))

        val target = File(dir, "work/res/xml/c.xml")
        assertTrue(target.lastModified() < System.currentTimeMillis() - 30_000)
    }

    @Test
    fun `close drops what was never asked for and closes the module`() {
        val files = lazy()
        files.awaitFor(File(dir, "work/res/xml/c.xml"))
        files.close()

        assertEquals(listOf("res/xml/c.xml"), decoded)
        assertTrue(moduleClosed)
        files.awaitAll()
        assertEquals(listOf("res/xml/c.xml"), decoded)
    }

    @Test
    fun `an entry missing from the input APK fails the await that asks for it`() {
        val files = LazyXmlFiles(apk, module, onWritten = {}).also { closeables += it }
        files.defer(File(dir, "work/res/xml/gone.xml"), "res/xml/gone.xml") { }

        assertFailsWith<IOException> { files.awaitAll() }
    }
}
