/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.apk

import com.reandroid.apk.ApkModule
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

internal class ApkMergerSplitManifestTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `manifest without an application element merges`() {
        val splitDir = File(tempDir, "splits").apply { mkdirs() }
        ApkModule().use { module ->
            val manifest = AndroidManifestBlock()
            manifest.packageName = "com.test.split"
            manifest.versionCode = 1
            manifest.versionName = "1.0"
            manifest.setMinSdkVersion(21)
            module.setManifest(manifest)
            // A manifest is only taken as the merge base with a main activity, so the module
            // gets a resource table instead and ARSCLib picks it as the largest table module.
            module.ensureTableBlock()
            module.writeApk(File(splitDir, "split_config.arm64_v8a.apk"))
        }
        val output = File(tempDir, "merged.apk")

        ApkMerger().merge(splitDir, output, validateModules = false, validateResDir = false)

        assertTrue(output.isFile)
    }
}
