/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.patch

import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.resource.ResourceIds
import app.morphe.patcher.resource.coder.ResourceCoder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertNull

internal class ResourcePatchContextFailureTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `a context that fails to read its package metadata closes its coder and clears the resource ids`() {
        val coder = mockk<ResourceCoder>(relaxed = true)
        every { coder.getPackageMetadata() } throws IllegalStateException("broken resource table")
        val config = PatcherConfig(File(tempDir, "input.apk"))

        assertFails { ResourcePatchContext(config) { coder } }

        verify { coder.close() }
        assertNull(ResourceIds.current)
    }
}
