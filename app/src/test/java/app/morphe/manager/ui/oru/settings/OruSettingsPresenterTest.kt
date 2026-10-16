/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.settings

import app.morphe.manager.ui.oru.theme.OruGlassStyle
import app.morphe.manager.ui.oru.theme.OruThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OruSettingsPresenterTest {

    private fun input(
        sdkInt: Int = 34,
        themeMode: OruThemeMode = OruThemeMode.System,
        glassStyle: OruGlassStyle = OruGlassStyle.Frosted,
        animations: Boolean = true,
        keepAppsUpdated: Boolean = true,
        allowMeteredUpdates: Boolean = true,
        reclaimableBytes: Long = 0L,
    ) = OruSettingsInput(
        sdkInt = sdkInt,
        themeMode = themeMode,
        glassStyle = glassStyle,
        animations = animations,
        wallpaperColors = false,
        keepAppsUpdated = keepAppsUpdated,
        allowMeteredUpdates = allowMeteredUpdates,
        reclaimableBytes = reclaimableBytes,
        languageLabel = "",
        versionName = "1.0",
    )

    private fun OruSettingsUiState.rowsOf(section: OruSettingsSection): List<OruSettingsRowUi> =
        sections.first { it.section == section }.rows

    private fun List<OruSettingsRowUi>.ids() = map { it.id }

    @Test
    fun sectionsKeepTheDesignOrder() {
        val state = OruSettingsPresenter.present(input())

        assertEquals(
            listOf(
                OruSettingsSection.APPEARANCE,
                OruSettingsSection.LANGUAGE,
                OruSettingsSection.UPDATES,
                OruSettingsSection.STORAGE,
                OruSettingsSection.ABOUT,
                OruSettingsSection.ADVANCED,
            ),
            state.sections.map { it.section },
        )
    }

    @Test
    fun appearanceHoldsThemeGlassAnimationsAndWallpaperColors() {
        val android12 = OruSettingsPresenter.present(input(sdkInt = 31)).rowsOf(OruSettingsSection.APPEARANCE)
        assertEquals(
            listOf(
                OruSettingsRowId.THEME,
                OruSettingsRowId.GLASS,
                OruSettingsRowId.ANIMATIONS,
                OruSettingsRowId.WALLPAPER_COLORS,
            ),
            android12.ids(),
        )
    }

    @Test
    fun wallpaperColorsNeedAndroid12() {
        val android11 = OruSettingsPresenter.present(input(sdkInt = 30)).rowsOf(OruSettingsSection.APPEARANCE)

        assertEquals(listOf(OruSettingsRowId.THEME, OruSettingsRowId.GLASS, OruSettingsRowId.ANIMATIONS), android11.ids())
    }

    @Test
    fun animationsSwitchCarriesItsStoredValue() {
        val on = OruSettingsPresenter.present(input(animations = true)).rowsOf(OruSettingsSection.APPEARANCE)
        val off = OruSettingsPresenter.present(input(animations = false)).rowsOf(OruSettingsSection.APPEARANCE)

        assertEquals(OruSettingsValue.Toggle(true), on.value(OruSettingsRowId.ANIMATIONS))
        assertEquals(OruSettingsValue.Toggle(false), off.value(OruSettingsRowId.ANIMATIONS))
    }

    @Test
    fun mobileDataIsRevealedOnlyWhileUpdatesAreOn() {
        val on = OruSettingsPresenter.present(input(keepAppsUpdated = true)).rowsOf(OruSettingsSection.UPDATES)
        val off = OruSettingsPresenter.present(input(keepAppsUpdated = false)).rowsOf(OruSettingsSection.UPDATES)

        assertEquals(listOf(OruSettingsRowId.KEEP_APPS_UPDATED, OruSettingsRowId.MOBILE_DATA_UPDATES), on.ids())
        assertTrue(on.single { it.id == OruSettingsRowId.MOBILE_DATA_UPDATES }.shown)
        assertFalse(off.single { it.id == OruSettingsRowId.MOBILE_DATA_UPDATES }.shown)
    }

    @Test
    fun storageHoldsFreeUpSpaceAndSigningKey() {
        val storage = OruSettingsPresenter.present(input()).rowsOf(OruSettingsSection.STORAGE)

        assertEquals(listOf(OruSettingsRowId.FREE_UP_SPACE, OruSettingsRowId.SIGNING_KEY), storage.ids())
    }

    @Test
    fun advancedIsTheLastRowAndTheOnlyDoorToPowerSettings() {
        val state = OruSettingsPresenter.present(input())

        assertEquals(
            listOf(OruSettingsRowId.ADVANCED),
            state.rowsOf(OruSettingsSection.ADVANCED).ids(),
        )
        assertEquals(OruSettingsValue.Link, state.rowsOf(OruSettingsSection.ADVANCED).single().value)
        val everyId = state.sections.flatMap { it.rows }.ids()
        assertEquals(OruSettingsRowId.ADVANCED, everyId.last())
    }

    @Test
    fun valuesCarryThroughToTheRows() {
        val state = OruSettingsPresenter.present(
            input(
                themeMode = OruThemeMode.Dark,
                glassStyle = OruGlassStyle.Clear,
                reclaimableBytes = 1_200L,
            ).copy(wallpaperColors = true, keepAppsUpdated = false, allowMeteredUpdates = false)
        )

        assertEquals(OruSettingsValue.Theme(OruThemeMode.Dark), state.rowsOf(OruSettingsSection.APPEARANCE).value(OruSettingsRowId.THEME))
        assertEquals(OruSettingsValue.Glass(OruGlassStyle.Clear), state.rowsOf(OruSettingsSection.APPEARANCE).value(OruSettingsRowId.GLASS))
        assertEquals(OruSettingsValue.Toggle(true), state.rowsOf(OruSettingsSection.APPEARANCE).value(OruSettingsRowId.WALLPAPER_COLORS))
        assertEquals(OruSettingsValue.Toggle(false), state.rowsOf(OruSettingsSection.UPDATES).value(OruSettingsRowId.KEEP_APPS_UPDATED))
        assertEquals(OruSettingsValue.Toggle(false), state.rowsOf(OruSettingsSection.UPDATES).value(OruSettingsRowId.MOBILE_DATA_UPDATES))
        assertEquals(OruSettingsValue.Bytes(1_200L), state.rowsOf(OruSettingsSection.STORAGE).value(OruSettingsRowId.FREE_UP_SPACE))
        assertEquals(OruSettingsValue.Text("1.0"), state.rowsOf(OruSettingsSection.ABOUT).value(OruSettingsRowId.ABOUT))
    }

    private fun List<OruSettingsRowUi>.value(id: OruSettingsRowId): OruSettingsValue? =
        firstOrNull { it.id == id }?.value
}
