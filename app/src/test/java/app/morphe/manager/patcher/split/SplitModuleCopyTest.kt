/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.patcher.split

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SplitModuleCopyTest {
    private lateinit var root: File
    private lateinit var zipFile: File
    private lateinit var targetDir: File
    private val modules = linkedMapOf(
        "base.apk" to randomBytes(seed = 1, size = 300_000),
        "split_config.arm64_v8a.apk" to randomBytes(seed = 2, size = 0),
        "split_config.xxhdpi.apk" to randomBytes(seed = 3, size = 1),
        "split_config.en.apk" to randomBytes(seed = 4, size = 8191),
        "split_config.de.apk" to randomBytes(seed = 5, size = 8192),
        "split_config.ko.apk" to randomBytes(seed = 6, size = 123_457),
    )

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("split-copy-test").toFile()
        zipFile = File(root, "bundle.apks")
        targetDir = File(root, "modules").apply { mkdirs() }
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            modules.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `copies are byte identical and keep the entry order for any thread count`() {
        for (threads in listOf(1, 2, 3, 8)) {
            val dir = File(targetDir, "threads-$threads").apply { mkdirs() }

            val extracted = ZipFile(zipFile).use { zip ->
                SplitApkPreparer.copySplitModules(zip, entries(zip), dir, threads)
            }

            assertEquals(modules.keys.toList(), extracted.map { it.name }, "threads=$threads")
            modules.forEach { (name, bytes) ->
                assertContentEquals(bytes, File(dir, name).readBytes(), "$name with threads=$threads")
            }
        }
    }

    @Test
    fun `three copies run at the same time`() {
        // Sequential copies would never reach the barrier, so the await would time out and fail the copy
        val barrier = CyclicBarrier(3)

        val extracted = ZipFile(zipFile).use { zip ->
            SplitApkPreparer.copySplitModules(
                zip = zip,
                entries = entries(zip).take(3),
                targetDir = targetDir,
                threads = 3,
                onCopyStarted = { barrier.await(10, TimeUnit.SECONDS) }
            )
        }

        assertEquals(3, extracted.size)
    }

    @Test
    fun `no more copies run at once than there are threads`() {
        val inFlight = AtomicInteger(0)
        val peak = AtomicInteger(0)

        ZipFile(zipFile).use { zip ->
            SplitApkPreparer.copySplitModules(
                zip = zip,
                entries = entries(zip),
                targetDir = targetDir,
                threads = 3,
                onCopyStarted = {
                    peak.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
                    Thread.sleep(20)
                    inFlight.decrementAndGet()
                }
            )
        }

        assertTrue(peak.get() in 1..3, "peak was ${peak.get()}")
    }

    @Test
    fun `a failed copy is rethrown as its own exception`() {
        // A directory where a module should be written makes that one copy fail
        File(targetDir, "split_config.en.apk").mkdirs()

        val thrown = runCatching {
            ZipFile(zipFile).use { zip ->
                SplitApkPreparer.copySplitModules(zip, entries(zip), targetDir, threads = 3)
            }
        }.exceptionOrNull()

        assertIs<IOException>(thrown)
    }

    private fun entries(zip: ZipFile): List<ZipEntry> = zip.entries().asSequence().toList()

    private companion object {
        fun randomBytes(seed: Int, size: Int): ByteArray = Random(seed).nextBytes(size)
    }
}
