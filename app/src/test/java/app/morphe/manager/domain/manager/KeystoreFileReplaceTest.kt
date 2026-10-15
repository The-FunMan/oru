/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.manager

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KeystoreFileReplaceTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `replacing a keystore leaves the new bytes and no temporary file`() {
        val target = folder.root.toPath().resolve("morphe.keystore")
        Files.write(target, byteArrayOf(1, 2, 3))

        replaceFileAtomically(target, byteArrayOf(4, 5, 6, 7))

        assertContentEquals(byteArrayOf(4, 5, 6, 7), Files.readAllBytes(target))
        assertEquals(listOf("morphe.keystore"), folder.root.list()!!.sorted())
    }

    @Test
    fun `a failed replace keeps no temporary file behind`() {
        val target = folder.root.toPath().resolve("morphe.keystore")
        // A non-empty directory cannot be replaced, so the final move fails after the temp file exists
        Files.createDirectories(target.resolve("occupied"))

        assertFailsWith<IOException> { replaceFileAtomically(target, byteArrayOf(9)) }

        assertEquals(listOf("morphe.keystore"), folder.root.list()!!.sorted())
        assertEquals(true, Files.isDirectory(target))
    }
}
