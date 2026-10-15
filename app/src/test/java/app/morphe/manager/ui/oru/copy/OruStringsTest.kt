/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.copy

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Checks the Oru copy files by reading them. Runs without the Android toolchain.
 *
 * - `values/oru_strings.xml`: the copy for the Oru screens.
 * - `src/oru/res/values/strings.xml`: the English overlay for the oru flavour.
 */
class OruStringsTest {

    private companion object {
        val EM_DASH = Char(0x2014)
        val EN_DASH = Char(0x2013)
    }

    /** Every Oru copy file of the main module: oru_strings.xml and each oru_strings_*.xml beside it. */
    private val oruCopy: List<File> = resolve("src/main/res/values").listFiles().orEmpty()
        .filter { it.isFile && it.name.startsWith("oru_strings") && it.name.endsWith(".xml") }
        .sortedBy { it.name }

    /** The strings of every Oru copy file, read once. A key counts when any of the files holds it. */
    private val copyEntries: List<Pair<String, String>> by lazy { oruCopy.flatMap { readEntries(it) } }

    /** Copy that is shown as it is, with no format call, so a bare percent sign is the text the user reads. */
    private val unformattedKeys = setOf("oru_vital_unit_percent")
    private val overlay = resolve("src/oru/res/values/strings.xml")
    private val upstream = resolve("src/main/res/values/strings.xml")
    private val sourceStrings = resolve("src/main/res/values/oru_strings_sources.xml")

    /** Overlay strings that still name the upstream project on purpose (see the overlay commit). */
    private val upstreamAttributionAllowed = setOf("sources_dialog_community_notice_message")

    /** Keys the Oru screens read. Renaming one of these without updating the screen breaks the build. */
    private val requiredKeys = listOf(
        "oru_action_cancel",
        "oru_action_try_again",
        "oru_home_subtitle",
        "oru_mode_simple",
        "oru_mode_expert",
        "oru_chip_apk_needed",
        "oru_chip_waiting_apk",
        "oru_chip_ready",
        "oru_chip_installed",
        "oru_action_get_apk",
        "oru_action_find_apkmirror",
        "oru_action_use_file",
        "oru_action_choose_file",
        "oru_action_open_apkmirror_again",
        "oru_action_get_it",
        "oru_action_patch",
        "oru_action_open",
        "oru_banner_manager_update",
        "oru_banner_metered",
        "oru_banner_batch_updates",
        "oru_banner_offline",
        "oru_banner_stale_patches",
        "oru_focus_version",
        "oru_focus_patches",
        "oru_focus_channel",
        "oru_focus_channel_stable",
        "oru_focus_channel_prerelease",
        "oru_focus_install_with",
        "oru_focus_dock_patch",
        "oru_simple_customized",
        "oru_simple_status_update_ready",
        "oru_notice_experimental_on",
        "oru_notice_experimental_off",
        "oru_notice_action_pick_version",
        "oru_notice_action_show_experimental",
        "oru_stage_load_patches",
        "oru_stage_split_apk",
        "oru_stage_read_apk",
        "oru_stage_execute_patches",
        "oru_stage_write_apk",
        "oru_stage_sign_apk",
        "oru_run_title_patching",
        "oru_run_title_upgrading",
        "oru_run_cancel_go",
        "oru_vital_heap",
        "oru_vital_cpu",
        "oru_vital_io",
        "oru_action_install",
        "oru_action_installing",
        "oru_action_installed",
        "oru_signature_title",
        "oru_signature_body",
        "oru_signature_hint_import",
        "oru_signature_go",
        "oru_key_notice_title",
        "oru_failed_title",
        "oru_memory_title",
        "oru_memory_action_lower",
        "oru_heap_exhausted",
        "oru_settings_title",
        "oru_settings_animations",
        "oru_settings_animations_helper",
        "oru_settings_wallpaper",
        "oru_settings_updates",
        "oru_settings_free_space",
        "oru_settings_installer_ask",
        "oru_settings_prerelease_sources",
        "oru_settings_other_apps",
        "oru_settings_patch_sources",
        "oru_settings_signing_key",
        "oru_settings_export_log",
        "oru_settings_about"
    )

    /** Stage labels must be the pipeline's own step strings, not new wording. */
    private val stageLabelSources = mapOf(
        "oru_stage_load_patches" to "patcher_step_load_patches",
        "oru_stage_split_apk" to "patcher_step_prepare_split_apk",
        "oru_stage_read_apk" to "patcher_step_unpack",
        "oru_stage_execute_patches" to "applying_patches",
        "oru_stage_write_apk" to "patcher_step_write_patched",
        "oru_stage_sign_apk" to "patcher_step_sign_apk"
    )

    @Test
    fun oruStringsParseWithUniqueNames() {
        val entries = copyEntries
        val names = entries.map { it.first }
        assertEquals(names.size, names.toSet().size, "Duplicate names across the Oru copy files: ${names.groupBy { it }.filter { it.value.size > 1 }.keys}")
        assertTrue(entries.isNotEmpty(), "The Oru copy files have no strings")
    }

    @Test
    fun requiredKeysExist() {
        val names = copyEntries.map { it.first }.toSet()
        val missing = requiredKeys.filterNot { it in names }
        assertTrue(missing.isEmpty(), "Keys missing from the Oru copy files: $missing")
    }

    @Test
    fun oruStringsNeverNameMorphe() {
        val raw = oruCopy.joinToString("") { it.readText() }
        assertFalse(raw.contains("morphe", ignoreCase = true), "The Oru copy files must not contain Morphe")
    }

    @Test
    fun oruStringsHaveNoDashPunctuation() {
        val raw = oruCopy.joinToString("") { it.readText() }
        assertFalse(raw.contains(EM_DASH), "The Oru copy files contain an em dash")
        assertFalse(raw.contains(EN_DASH), "The Oru copy files contain an en dash")
    }

    @Test
    fun oruStringsAvoidSpeedClaims() {
        val banned = Regex("\\b(fast|instant|blazing)\\b", RegexOption.IGNORE_CASE)
        val hits = copyEntries.filter { banned.containsMatchIn(it.second) }.map { it.first }
        assertTrue(hits.isEmpty(), "Speed claims in copy: $hits")
    }

    @Test
    fun copyIsPlainAscii() {
        val entries = copyEntries + readEntries(overlay)
        val hits = entries.filterNot { (_, text) -> text.all { it.code < 0x80 } }.map { it.first }
        assertTrue(hits.isEmpty(), "Non-ASCII characters (emoji or typographic marks) in copy: $hits")
    }

    @Test
    fun apostrophesAreEscapedInCopyFiles() {
        for (file in oruCopy + overlay) {
            val body = file.readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
            val bare = Regex("(?<!\\\\)'").findAll(body).count()
            assertEquals(0, bare, "${file.name} has apostrophes that are not escaped as \\'")
        }
    }

    /**
     * Whether [text] has a percent sign that is neither half of a `%%` pair nor a format specifier. The pairs go first,
     * so the second sign of `%%` is never read on its own.
     */
    private fun hasBarePercent(text: String): Boolean {
        val withoutPairs = text.replace("%%", "")
        val withoutSpecifiers = withoutPairs.replace(Regex("%(\\d+\\$)?[sdf]"), "")
        return withoutSpecifiers.contains('%')
    }

    @Test
    fun aDoubledPercentSignAfterAPositionalArgumentIsLiteral() {
        assertFalse(hasBarePercent("Downloading the Oru update, %1\$d%%."))
        assertFalse(hasBarePercent("%%%d"))
        assertTrue(hasBarePercent("50% done"))
    }

    @Test
    fun formatSpecifiersAreValid() {
        for (file in oruCopy + overlay) {
            for ((name, text) in readEntries(file)) {
                if (name in unformattedKeys) continue
                assertFalse(hasBarePercent(text), "${file.name}:$name has a bare % (write %% for a literal percent sign)")
            }
        }
    }

    @Test
    fun stringsWithSeveralArgumentsUsePositionalFormats() {
        val spec = Regex("%(\\d+\\$)?[sdf]")
        for (file in oruCopy + overlay) {
            for ((name, text) in readEntries(file)) {
                val specs = spec.findAll(text.replace("%%", "")).toList()
                if (specs.size < 2) continue
                val unpositional = specs.filter { it.groups[1] == null }
                assertTrue(
                    unpositional.isEmpty(),
                    "${file.name}:$name has ${specs.size} arguments and must use %1\$s style positions"
                )
            }
        }
    }

    @Test
    fun stageLabelsAreThePipelineStepStrings() {
        val oru = copyEntries.toMap()
        val base = readEntries(upstream).toMap()
        for ((oruName, upstreamName) in stageLabelSources) {
            assertEquals(
                base[upstreamName],
                oru[oruName],
                "$oruName must match the pipeline label $upstreamName"
            )
        }
    }

    @Test
    fun overlayOverridesExistingKeysOnly() {
        val base = readEntries(upstream).map { it.first }.toSet()
        val orphans = readEntries(overlay).map { it.first }.filterNot { it in base }
        assertTrue(orphans.isEmpty(), "Overlay keys that do not exist in values/strings.xml: $orphans")
    }

    @Test
    fun overlayUsesOruNotMorphe() {
        val hits = readEntries(overlay)
            .filter { (name, text) -> text.contains("morphe", ignoreCase = true) && name !in upstreamAttributionAllowed }
            .map { it.first }
        assertTrue(hits.isEmpty(), "Overlay still names Morphe in: $hits")
    }

    @Test
    fun overlayHasNoDashPunctuation() {
        val raw = overlay.readText()
        assertFalse(raw.contains(EM_DASH) || raw.contains(EN_DASH), "Overlay contains an em or en dash")
    }

    @Test
    fun runLineStringsExistAndStayPlain() {
        val runStrings = resolve("src/main/res/values/oru_strings_run.xml")
        val names = readEntries(runStrings).map { it.first }.toSet()
        val lineKeys = listOf(
            "oru_run_line_reading_app",
            "oru_run_line_writing_app",
            "oru_run_line_signing_app",
            "oru_run_line_almost_there",
        )
        assertTrue(lineKeys.all { it in names }, "Run line keys missing from oru_strings_run.xml: ${lineKeys - names}")
        val raw = runStrings.readText()
        assertFalse(raw.contains("morphe", ignoreCase = true), "oru_strings_run.xml must not contain Morphe")
        assertFalse(raw.contains(EM_DASH) || raw.contains(EN_DASH), "oru_strings_run.xml contains an em or en dash")
    }

    @Test
    fun `the source choice copy never names Morphe or uses dashes`() {
        val raw = sourceStrings.readText()
        assertFalse(raw.contains("morphe", ignoreCase = true), "oru_strings_sources.xml must not contain Morphe")
        assertFalse(raw.contains(EM_DASH) || raw.contains(EN_DASH), "source copy must not use dashes")
    }
}
