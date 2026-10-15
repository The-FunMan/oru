package app.morphe.patcher.resource.coder

import java.io.Closeable
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The decode module of the lazy binary XML files is released once the decoder has sealed the queue and no
 * decode is pending, so it is not held through the encoding. These tests count the module closes and the
 * decodes, and check that nothing is decoded or queued after the release.
 */
internal class LazyXmlFilesReleaseTest {
    private lateinit var dir: File
    private lateinit var apk: File
    private val decoded = mutableListOf<String>()
    private var moduleCloses = 0
    private val module = Closeable { moduleCloses++ }
    private var lazy: LazyXmlFiles? = null

    @BeforeTest
    fun setUp() {
        dir = kotlin.io.path.createTempDirectory("lazy-xml-release").toFile()
        apk = File(dir, "app.apk")
        ZipOutputStream(apk.outputStream()).use { zip ->
            for (name in listOf("res/layout/a.xml", "res/layout/b.xml", "res/layout/c.xml")) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(name.toByteArray())
                zip.closeEntry()
            }
        }
    }

    @AfterTest
    fun tearDown() {
        lazy?.close()
        dir.deleteRecursively()
    }

    private fun defer(name: String) {
        val target = File(dir, "work/$name")
        lazy!!.defer(target, name) { input -> decoded += String(input.readBytes()) }
    }

    private fun create() {
        lazy = LazyXmlFiles(apk, module, onWritten = {})
    }

    @Test
    fun `the module stays open until the queue is sealed`() {
        create()
        defer("res/layout/a.xml")

        lazy!!.awaitFor(File(dir, "work/res/layout/a.xml"))

        assertEquals(listOf("res/layout/a.xml"), decoded)
        assertEquals(0, moduleCloses)
    }

    @Test
    fun `sealing with nothing pending releases the module`() {
        create()
        lazy!!.seal()

        assertEquals(1, moduleCloses)
    }

    @Test
    fun `the module is released by the last decode once sealed`() {
        create()
        defer("res/layout/a.xml")
        defer("res/layout/b.xml")
        lazy!!.seal()

        lazy!!.awaitFor(File(dir, "work/res/layout/a.xml"))
        assertEquals(0, moduleCloses)

        lazy!!.awaitFor(File(dir, "work/res/layout/b.xml"))

        assertEquals(listOf("res/layout/a.xml", "res/layout/b.xml"), decoded)
        assertEquals(1, moduleCloses)
    }

    @Test
    fun `awaitAll releases the module once everything is decoded`() {
        create()
        defer("res/layout/a.xml")
        defer("res/layout/b.xml")
        lazy!!.seal()

        lazy!!.awaitAll()

        assertEquals(listOf("res/layout/a.xml", "res/layout/b.xml"), decoded)
        assertEquals(1, moduleCloses)
    }

    @Test
    fun `nothing is decoded after the release and closing again releases nothing twice`() {
        create()
        defer("res/layout/a.xml")
        lazy!!.seal()
        lazy!!.awaitAll()

        lazy!!.awaitAll()
        lazy!!.awaitFor(File(dir, "work/res/layout/a.xml"))
        lazy!!.close()
        lazy!!.close()

        assertEquals(listOf("res/layout/a.xml"), decoded)
        assertEquals(1, moduleCloses)
    }

    @Test
    fun `no file can be deferred once the queue is sealed`() {
        create()
        defer("res/layout/a.xml")
        lazy!!.seal()
        lazy!!.awaitAll()

        assertFailsWith<IllegalStateException> { defer("res/layout/c.xml") }
        assertEquals(listOf("res/layout/a.xml"), decoded)
        assertEquals(1, moduleCloses)
    }

    @Test
    fun `closing with decodes pending releases the module without running them`() {
        create()
        defer("res/layout/a.xml")
        defer("res/layout/b.xml")

        lazy!!.close()

        assertEquals(emptyList(), decoded)
        assertEquals(1, moduleCloses)
    }
}
