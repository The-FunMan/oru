/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.settings

import app.morphe.manager.ui.oru.theme.OruGlassStyle
import app.morphe.manager.ui.oru.theme.OruThemeMode

/** Lowest API level with platform dynamic colour (Android 12). */
const val WALLPAPER_COLORS_MIN_SDK = 31

/**
 * Plain values the sheet needs, read from preferences and the environment by the ViewModel.
 * Nothing here touches Android, so [OruSettingsPresenter] runs in plain JVM tests.
 */
data class OruSettingsInput(
    val sdkInt: Int,
    val themeMode: OruThemeMode,
    val glassStyle: OruGlassStyle,
    val animations: Boolean,
    val wallpaperColors: Boolean,
    val keepAppsUpdated: Boolean,
    val allowMeteredUpdates: Boolean,
    val reclaimableBytes: Long,
    val languageLabel: String,
    val versionName: String,
)

/**
 * Maps [OruSettingsInput] to the six groups of the Settings flyout, in sheet order. Advanced and the other power
 * settings are not here: the Advanced row opens its own page.
 */
object OruSettingsPresenter {

    fun present(input: OruSettingsInput): OruSettingsUiState {
        val appearance = buildList {
            add(row(OruSettingsRowId.THEME, OruSettingsValue.Theme(input.themeMode)))
            add(row(OruSettingsRowId.GLASS, OruSettingsValue.Glass(input.glassStyle)))
            add(row(OruSettingsRowId.ANIMATIONS, OruSettingsValue.Toggle(input.animations)))
            if (input.sdkInt >= WALLPAPER_COLORS_MIN_SDK) {
                add(row(OruSettingsRowId.WALLPAPER_COLORS, OruSettingsValue.Toggle(input.wallpaperColors)))
            }
        }
        val updates = listOf(
            row(OruSettingsRowId.KEEP_APPS_UPDATED, OruSettingsValue.Toggle(input.keepAppsUpdated)),
            // Only asked about while updates are on, so the row is revealed under the switch
            OruSettingsRowUi(
                id = OruSettingsRowId.MOBILE_DATA_UPDATES,
                value = OruSettingsValue.Toggle(input.allowMeteredUpdates),
                shown = input.keepAppsUpdated,
            ),
        )
        val storage = listOf(
            row(OruSettingsRowId.FREE_UP_SPACE, OruSettingsValue.Bytes(input.reclaimableBytes)),
            row(OruSettingsRowId.SIGNING_KEY, OruSettingsValue.Link),
        )

        return OruSettingsUiState(
            sections = listOf(
                section(OruSettingsSection.APPEARANCE, appearance),
                section(
                    OruSettingsSection.LANGUAGE,
                    listOf(row(OruSettingsRowId.LANGUAGE, OruSettingsValue.Text(input.languageLabel))),
                ),
                section(OruSettingsSection.UPDATES, updates),
                section(OruSettingsSection.STORAGE, storage),
                section(
                    OruSettingsSection.ABOUT,
                    listOf(row(OruSettingsRowId.ABOUT, OruSettingsValue.Text(input.versionName))),
                ),
                section(
                    OruSettingsSection.ADVANCED,
                    listOf(row(OruSettingsRowId.ADVANCED, OruSettingsValue.Link)),
                ),
            ),
        )
    }

    private fun section(section: OruSettingsSection, rows: List<OruSettingsRowUi>) =
        OruSettingsSectionUi(section, rows)

    private fun row(id: OruSettingsRowId, value: OruSettingsValue) = OruSettingsRowUi(id, value)
}
