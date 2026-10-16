package app.morphe.patcher.resource.coder

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The input APK must be deletable once the lazy files are closed. On Windows an open handle makes the
 * delete fail, which is how a leaked ZipFile shows up in the temp directory cleanup of the tests.
 */
internal class LazyResFileHandleTest {
    private lateinit var dir: File
    private lateinit var apk: File
    private val bytes = ByteArray(100_000) { (it % 251).toByte() }
    private val closeables = mutableListOf<LazyResFiles>()

    @BeforeTest
    fun setUp() {
        dir = kotlin.io.path.createTempDirectory("lazy-handle").toFile()
        apk = File(dir, "app.apk")
        ZipOutputStream(apk.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("res/a.png"))
            zip.write(bytes)
            zip.closeEntry()
        }
    }

    @AfterTest
    fun tearDown() {
        closeables.forEach { it.close() }
        dir.deleteRecursively()
    }

    /**
     * Deletes the APK, retrying for a while: a virus scanner may hold the file for a moment after the close.
     * A handle that stays open fails every retry.
     */
    private fun assertDeletable(file: File) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!file.delete()) {
            assertTrue(System.nanoTime() < deadline, "the input APK is still open after close")
            Thread.sleep(10)
        }
    }

    @Test
    fun `closing after an entry was read releases the input APK`() {
        val lazy = LazyResFiles(apk, 0).also { closeables += it }
        lazy.open("res/a.png").use { assertContentEquals(bytes, it.readBytes()) }
        lazy.close()
        assertDeletable(apk)
    }

    @Test
    fun `starting the writers while a stream is open keeps the input APK until the stream is closed`() {
        val lazy = LazyResFiles(apk, 0).also { closeables += it }
        val stream = lazy.open("res/a.png")
        lazy.start()

        assertContentEquals(bytes, stream.readBytes())
        stream.close()
        lazy.close()
        assertDeletable(apk)
    }

    @Test
    fun `close does not return while a stream is open and returns once it is closed`() {
        val lazy = LazyResFiles(apk, 0).also { closeables += it }
        val stream = lazy.open("res/a.png")
        val closing = Thread { lazy.close() }.apply { start() }

        closing.join(1_000)
        assertTrue(closing.isAlive, "close returned while a stream was still open")
        assertContentEquals(bytes, stream.readBytes())
        stream.close()
        closing.join(10_000)
        assertFalse(closing.isAlive, "close did not return after the stream was closed")
        assertDeletable(apk)
    }

    @Test
    fun `closing while a write is in flight leaves no partial file and releases the input APK`() {
        val entered = CountDownLatch(1)
        val lazy = LazyResFiles(
            apk,
            workerCount = 1,
            afterCopy = {
                entered.countDown()
                Thread.sleep(10_000) // close interrupts the writer, which ends this wait
            },
        ).also { closeables += it }
        val target = File(dir, "work/res/drawable/a.png")
        lazy.defer(target, "res/a.png")
        lazy.start()
        assertTrue(entered.await(10, TimeUnit.SECONDS))
        lazy.close()

        assertFalse(target.exists(), "a write cut short by close must not leave a partial file")
        assertDeletable(apk)
    }

    @Test
    fun `close returns only after a write that ignores interruption has ended`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val lazy = LazyResFiles(
            apk,
            workerCount = 1,
            afterCopy = {
                entered.countDown()
                // A stalled copy keeps going after close interrupts its thread
                while (release.count > 0) {
                    try {
                        release.await()
                    } catch (_: InterruptedException) {
                    }
                }
            },
        ).also { closeables += it }
        val target = File(dir, "work/res/drawable/a.png")
        lazy.defer(target, "res/a.png")
        lazy.start()
        assertTrue(entered.await(10, TimeUnit.SECONDS))
        val closing = Thread { lazy.close() }.apply { start() }

        closing.join(6_000) // longer than the bound close used to wait
        assertTrue(closing.isAlive, "close returned while a write was still in flight")
        release.countDown()
        closing.join()

        assertContentEquals(bytes, target.readBytes())
        assertDeletable(apk)
    }
}
