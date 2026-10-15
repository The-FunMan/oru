/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.patcher.runtime

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PatcherProfilePropertiesTest {
    private fun withDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("oru-profile-test").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `no marker file adds no properties and does not ask for the output directory`() = withDir { dir ->
        var asked = false

        val properties = patcherProfileProperties(File(dir, PROFILE_MARKER_FILE)) {
            asked = true
            dir
        }

        assertEquals(emptyList(), properties)
        assertFalse(asked)
    }

    @Test
    fun `marker file turns the profile on and points the output at the external files directory`() = withDir { dir ->
        File(dir, PROFILE_MARKER_FILE).writeText("")
        val external = File(dir, "external").apply { mkdirs() }

        val properties = patcherProfileProperties(File(dir, PROFILE_MARKER_FILE)) { external }

        assertEquals(
            listOf(
                "-Doru.profile=true",
                "-Doru.profile.out=${File(external, PROFILE_OUTPUT_FILE).absolutePath}",
            ),
            properties,
        )
    }

    @Test
    fun `marker file without an external directory turns the profile on without an output file`() = withDir { dir ->
        File(dir, PROFILE_MARKER_FILE).writeText("")

        val properties = patcherProfileProperties(File(dir, PROFILE_MARKER_FILE)) { null }

        assertEquals(listOf("-Doru.profile=true"), properties)
    }

    @Test
    fun `marker file lines pass the known switches on as system properties in file order`() = withDir { dir ->
        File(dir, PROFILE_MARKER_FILE).writeText("oru.patcher.overlap=false\n  oru.patcher.lazyXml=true \noru.packager=jvm\n")

        val properties = patcherProfileProperties(File(dir, PROFILE_MARKER_FILE)) { null }

        assertEquals(
            listOf(
                "-Doru.profile=true",
                "-Doru.patcher.overlap=false",
                "-Doru.patcher.lazyXml=true",
                "-Doru.packager=jvm",
            ),
            properties,
        )
    }

    @Test
    fun `marker file lines with unknown keys, rejected values or no value are ignored`() = withDir { dir ->
        File(dir, PROFILE_MARKER_FILE).writeText(
            "oru.patcher.overlap=maybe\n" +
                "oru.packager=native\n" +
                "oru.profile.out=/data/local/tmp/x\n" +
                "oru.patcher.overlap\n" +
                "-Doru.patcher.lazyXml=false\n",
        )

        val properties = patcherProfileProperties(File(dir, PROFILE_MARKER_FILE)) { null }

        assertEquals(listOf("-Doru.profile=true"), properties)
    }
}
