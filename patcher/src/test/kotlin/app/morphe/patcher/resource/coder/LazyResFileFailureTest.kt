package app.morphe.patcher.resource.coder

import java.io.File
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class LazyResFileFailureTest {
    private lateinit var dir: File
    private lateinit var apk: File
    private val closeables = mutableListOf<LazyResFiles>()

    @BeforeTest
    fun setUp() {
        dir = kotlin.io.path.createTempDirectory("lazy-failure").toFile()
        apk = File(dir, "app.apk")
        ZipOutputStream(apk.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("res/good.png"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }
    }

    @AfterTest
    fun tearDown() {
        closeables.forEach { it.close() }
        dir.deleteRecursively()
    }

    /** A target whose archive entry is missing, so its write fails on the worker. */
    private fun failing(lazy: LazyResFiles) = File(dir, "work/res/drawable/missing.png").also {
        lazy.defer(it, "res/missing.png")
    }

    /** Returns once no worker is writing, so a failure it records is in place. */
    private fun awaitIdle(lazy: LazyResFiles) {
        val inFlight = LazyResFiles::class.java.getDeclaredField("inFlight").apply { isAccessible = true }
        val lock = LazyResFiles::class.java.getDeclaredField("lock").apply { isAccessible = true }
        val deadline = System.nanoTime() + 10_000_000_000L
        while (true) {
            val idle = (lock.get(lazy) as ReentrantLock).let { l ->
                l.lock()
                try {
                    lazy.queued == 0 && (inFlight.get(lazy) as Set<*>).isEmpty()
                } finally {
                    l.unlock()
                }
            }
            if (idle) return
            assertTrue(System.nanoTime() < deadline, "the workers did not finish")
            Thread.sleep(1)
        }
    }

    @Test
    fun `a failed write is reported to awaitFor after the queue has drained`() {
        val lazy = LazyResFiles(apk, workerCount = 1).also { closeables += it }
        val target = failing(lazy)
        lazy.start()
        awaitIdle(lazy)

        assertFailsWith<IOException> { lazy.awaitFor(target) }
    }

    @Test
    fun `a failed write is reported to awaitAll after the queue has drained`() {
        val lazy = LazyResFiles(apk, workerCount = 1).also { closeables += it }
        failing(lazy)
        lazy.start()
        awaitIdle(lazy)

        assertFailsWith<IOException> { lazy.awaitAll() }
    }

    @Test
    fun `a failed write is reported only to the await of its own file`() {
        val lazy = LazyResFiles(apk, workerCount = 1).also { closeables += it }
        val failed = failing(lazy)
        val good = File(dir, "work/res/drawable/good.png")
        lazy.defer(good, "res/good.png")
        lazy.start()
        awaitIdle(lazy)

        lazy.awaitFor(good)
        assertFailsWith<IOException> { lazy.awaitFor(failed) }
    }
}
