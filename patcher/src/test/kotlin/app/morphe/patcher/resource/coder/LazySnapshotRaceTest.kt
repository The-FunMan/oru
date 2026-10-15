package app.morphe.patcher.resource.coder

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class LazySnapshotRaceTest {
    private lateinit var dir: File
    private lateinit var apk: File
    private lateinit var coder: ArsclibResourceCoder
    private lateinit var pkgDir: File
    private var lazy: LazyResFiles? = null
    private val content = ByteArray(4096) { (it % 251).toByte() }

    @BeforeTest
    fun setUp() {
        dir = createTempDirectory("lazy-snapshot-race").toFile()
        apk = File(dir, "app.apk")
        ZipOutputStream(apk.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("res/a.png"))
            zip.write(content)
            zip.closeEntry()
        }
        val workingDir = File(dir, "working").apply { mkdirs() }
        pkgDir = File(workingDir, "resources/0").apply { resolve("res/drawable").mkdirs() }
        coder = ArsclibResourceCoder(workingDir, apk)
        coder.packageDirectories["com.test.app"] = pkgDir
    }

    @AfterTest
    fun tearDown() {
        lazy?.close()
        coder.close()
        dir.deleteRecursively()
    }

    /**
     * The decode-time walk records a lazily written raw file while its writer has not finished it. The writer's
     * snapshot, taken once the file is complete, must replace the walk's, or the file is reported as modified by the
     * first change check. A later edit by a patch must still be reported.
     */
    @Test
    fun `raw file still being written at decode time is not reported as changed`() {
        val target = pkgDir.resolve("res/drawable/a.png")
        val copied = CountDownLatch(1)
        val release = CountDownLatch(1)
        val written = lazilyWrittenMap()
        val files = LazyResFiles(
            apk,
            workerCount = 1,
            onWritten = { file -> written[file.absoluteFile.invariantSeparatorsPath] = snapshotOf(file) },
            afterCopy = {
                copied.countDown()
                release.await(10, TimeUnit.SECONDS)
            },
        )
        lazy = files
        files.defer(target, "res/a.png")
        setLazyRes(files)
        files.start()
        check(copied.await(10, TimeUnit.SECONDS)) { "The worker never copied the raw file" }

        coder.fileSnapshotCache = coder.buildFileSnapshot()
        assertTrue(
            coder.fileSnapshotCache.containsKey(target.absoluteFile.invariantSeparatorsPath),
            "The decode-time walk should see the file being written",
        )

        release.countDown()
        coder.detectFileChanges()

        assertFalse(coder.modifiedResResources.contains(target), "A lazily written file must not look modified")
        assertTrue(content.contentEquals(target.readBytes()), "The writer should have finished the file")

        target.writeBytes(content + byteArrayOf(7))
        coder.detectFileChanges()

        assertTrue(coder.modifiedResResources.contains(target), "A patch edit of a lazily written file must be reported")
    }

    private fun setLazyRes(files: LazyResFiles) {
        ArsclibResourceCoder::class.java.getDeclaredField("lazyRes").apply { isAccessible = true }.set(coder, files)
    }

    @Suppress("UNCHECKED_CAST")
    private fun lazilyWrittenMap(): MutableMap<String, ArsclibResourceCoder.FileSnapshot> =
        ArsclibResourceCoder::class.java.getDeclaredField("lazilyWritten")
            .apply { isAccessible = true }
            .get(coder) as MutableMap<String, ArsclibResourceCoder.FileSnapshot>

    private fun snapshotOf(file: File): ArsclibResourceCoder.FileSnapshot {
        val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
        return ArsclibResourceCoder.FileSnapshot(
            attributes.creationTime(),
            attributes.lastModifiedTime(),
            attributes.size(),
            attributes.fileKey(),
        )
    }
}
