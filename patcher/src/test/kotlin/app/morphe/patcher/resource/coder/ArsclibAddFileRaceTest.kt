package app.morphe.patcher.resource.coder

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * Concurrency tests verifying that patch files added via [ArsclibResourceCoder.addFile]
 * take precedence over concurrently extracted raw resources.
 */
internal class ArsclibAddFileRaceTest {
    private lateinit var dir: File
    private lateinit var apk: File
    private lateinit var coder: ArsclibResourceCoder
    private var lazy: LazyResFiles? = null

    @BeforeTest
    fun setUp() {
        dir = createTempDirectory("add-file-race").toFile()
        apk = File(dir, "app.apk")
        ZipOutputStream(apk.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("res/a.png"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }
        val workingDir = File(dir, "working").apply { mkdirs() }
        coder = ArsclibResourceCoder(workingDir, apk)
    }

    @AfterTest
    fun tearDown() {
        lazy?.close()
        coder.close()
        dir.deleteRecursively()
    }

    /**
     * Verifies that when a patch adds a file to a resource path whose raw original is still being
     * extracted in the background, the patch file is preserved and not overwritten by the worker.
     */
    @Test
    fun `a patch file added while the raw original is being written is not replaced by it`() {
        val pkgDir = File(dir, "working/resources/0").apply { resolve("res/drawable").mkdirs() }
        coder.packageDirectories["com.test.app"] = pkgDir
        val target = pkgDir.resolve("res/drawable/a.png")

        val copied = CountDownLatch(1)
        val release = CountDownLatch(1)
        val adderWaiting = CountDownLatch(1)
        val files = LazyResFiles(apk, workerCount = 1, afterCopy = {
            copied.countDown()
            release.await(10, TimeUnit.SECONDS)
        }, beforeWait = { adderWaiting.countDown() })
        lazy = files
        files.defer(target, "res/a.png")
        setLazyRes(files)
        files.start()
        check(copied.await(10, TimeUnit.SECONDS)) { "The worker never copied the raw file" }

        val patchFile = File(dir, "patch.png").apply { writeBytes(byteArrayOf(9)) }
        var failure: Throwable? = null
        val adder = Thread {
            try {
                coder.addFile("res/drawable/a.png", patchFile, "com.test.app")
            } catch (t: Throwable) {
                failure = t
            }
        }.apply { start() }
        // The adder signals just before it waits on the in-flight worker, so the release lands during that wait.
        check(adderWaiting.await(10, TimeUnit.SECONDS)) { "The patch add never waited on the raw original" }
        release.countDown()
        adder.join(10_000)
        failure?.let { throw it }
        files.awaitAll()

        assertContentEquals(byteArrayOf(9), target.readBytes())
    }

    private fun setLazyRes(files: LazyResFiles) {
        ArsclibResourceCoder::class.java.getDeclaredField("lazyRes").apply { isAccessible = true }.set(coder, files)
    }
}
