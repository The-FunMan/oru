/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.copy

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Oru flavour is branded Oru. Checks every string file the flavour reads: the overlay in
 * `src/oru/res/values*` and the Oru copy in `src/main/res/values/oru_strings*.xml`.
 *
 * "Morphe" may only appear in the keys of [MORPHE_ALLOWED_KEYS].
 */
class OruVisibleBrandTest {

    private companion object {
        /**
         * Keys whose text keeps "Morphe" on purpose. Both are the custom branding instructions, which
         * name the image files the manager reads from the user's folders (`morphe_adaptive_*_custom.png`
         * and the like, see `PatchOptionsPreferencesManager` and `AdaptiveIconCreatorDialog`). Those
         * names are a contract with the user's folders, so they stay until a migration is decided.
         *
         * Credits, licences and About credits are not in these files: they live in `CreditsDialog.kt`
         * and in upstream strings that this flavour does not overlay, so no credits key is listed here.
         */
        val MORPHE_ALLOWED_KEYS = setOf(
            "settings_advanced_patch_options_custom_branding_custom_icon_instruction",
            "settings_advanced_patch_options_custom_header_instruction",
        )

        val MORPHE = Regex("morphe", RegexOption.IGNORE_CASE)
    }

    private val files: List<File> = copyFiles()

    @Test
    fun morpheIsNamedOnlyByAllowedKeys() {
        val hits = files.flatMap { file ->
            readEntries(file)
                .filter { (name, text) -> MORPHE.containsMatchIn(text) && name !in MORPHE_ALLOWED_KEYS }
                .map { (name, _) -> "${file.name}:$name" }
        }
        assertTrue(hits.isEmpty(), "Morphe is named outside the allow-list: $hits")
    }

    @Test
    fun allowedKeysStillNameMorphe() {
        val named = files.flatMap { readEntries(it) }
            .filter { (_, text) -> MORPHE.containsMatchIn(text) }
            .map { (name, _) -> name }
            .toSet()
        val stale = MORPHE_ALLOWED_KEYS - named
        assertTrue(stale.isEmpty(), "Allow-listed keys no longer name Morphe, drop them from the list: $stale")
    }

    @Test
    fun appNameIsOru() {
        val overlay = resolve("src/oru/res/values/strings.xml")
        assertEquals("Oru", readEntries(overlay).toMap()["app_name"])
    }

    /** Every XML file in the flavour's `values*` directories. */
    private fun oruValueFiles(): List<File> =
        resolve("src/oru/res").listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values") }
            .flatMap { dir -> dir.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".xml") } }

    /** The flavour's `values*` string files, plus the Oru copy in the main module. */
    private fun copyFiles(): List<File> {
        val overlays = oruValueFiles().filter { it.name == "strings.xml" || it.name.startsWith("oru") }
        val copy = resolve("src/main/res/values").listFiles().orEmpty()
            .filter { it.isFile && it.name.startsWith("oru_strings") && it.name.endsWith(".xml") }
        return (overlays + copy).sortedBy { it.path }
    }
}
