/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.settings

import app.morphe.manager.ui.oru.theme.OruGlassStyle
import app.morphe.manager.ui.oru.theme.OruThemeMode

/** The groups of the Settings flyout, top to bottom. Each group's copy is keyed by its name in oru_strings.xml. */
enum class OruSettingsSection { APPEARANCE, LANGUAGE, UPDATES, STORAGE, ABOUT, ADVANCED }

/** Every row the Settings flyout can show. Labels live in oru_strings.xml, keyed by the id. */
enum class OruSettingsRowId {
    THEME,
    GLASS,
    ANIMATIONS,
    WALLPAPER_COLORS,
    LANGUAGE,
    KEEP_APPS_UPDATED,
    MOBILE_DATA_UPDATES,
    FREE_UP_SPACE,
    SIGNING_KEY,
    ABOUT,
    ADVANCED,
}

/** What a row shows on its trailing edge, or the choice it holds. The sheet turns this into a switch, a value or a chevron. */
sealed interface OruSettingsValue {
    /** A switch with its current state. */
    data class Toggle(val on: Boolean) : OruSettingsValue

    /** Plain text, such as the phone language or the Oru version. */
    data class Text(val text: String) : OruSettingsValue

    /** Bytes that Free up space can remove. Zero means nothing to remove. */
    data class Bytes(val bytes: Long) : OruSettingsValue

    /** The theme segments: Follow the phone, Light or Dark. */
    data class Theme(val mode: OruThemeMode) : OruSettingsValue

    /** The glass segments: Frosted or Clear. */
    data class Glass(val style: OruGlassStyle) : OruSettingsValue

    /** Opens something or runs an action. No value is shown. */
    data object Link : OruSettingsValue
}

/**
 * One row. [shown] is false for a row that is revealed by another one, such as Use mobile data for updates under
 * Keep apps up to date. The sheet animates it in and out, so it stays in the list while it is hidden.
 */
data class OruSettingsRowUi(
    val id: OruSettingsRowId,
    val value: OruSettingsValue,
    val shown: Boolean = true,
)

data class OruSettingsSectionUi(
    val section: OruSettingsSection,
    val rows: List<OruSettingsRowUi>,
)

data class OruSettingsUiState(
    /** The groups in sheet order. */
    val sections: List<OruSettingsSectionUi>,
)

/** Writes from the sheet. Implemented by [OruSettingsViewModel]. */
interface OruSettingsEvents {
    /** A switch row was flipped to [on]. Only switch rows reach this. */
    fun onToggle(id: OruSettingsRowId, on: Boolean)

    /** A theme segment was tapped. */
    fun onThemeModeSelected(mode: OruThemeMode)

    /** A glass segment was tapped. */
    fun onGlassStyleSelected(style: OruGlassStyle)

    /** A language was picked in the picker. [code] is an [app.morphe.manager.util.AppLocale] code. */
    fun onLanguageSelected(code: String)
}
