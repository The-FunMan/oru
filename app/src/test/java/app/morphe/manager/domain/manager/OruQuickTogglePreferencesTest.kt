/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.manager

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.morphe.manager.domain.manager.base.EnumPreference
import app.morphe.manager.ui.oru.theme.OruGlassStyle
import app.morphe.manager.ui.oru.theme.OruThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The two Home quick toggle preferences, built the way [PreferencesManager] builds them. The keys and defaults
 * are the ones declared there; [PreferencesManager] itself needs an Android context, so it is not built here.
 */
class OruQuickTogglePreferencesTest {

    private val themeKey = "oru_theme_mode"
    private val glassKey = "oru_glass_style"

    private fun themePref(store: DataStore<Preferences>) =
        EnumPreference(store, themeKey, OruThemeMode.System, enumValues<OruThemeMode>())

    private fun glassPref(store: DataStore<Preferences>) =
        EnumPreference(store, glassKey, OruGlassStyle.Frosted, enumValues<OruGlassStyle>())

    /** Runs [block] on a fresh store file, so no test sees another test's values. */
    private fun withStore(block: suspend (DataStore<Preferences>) -> Unit) = runBlocking {
        val file = File.createTempFile("oru-quick-toggles", ".preferences_pb").apply {
            delete() // DataStore creates the file itself
            deleteOnExit()
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            block(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `theme mode defaults to System when nothing is stored`() = withStore { store ->
        assertEquals(OruThemeMode.System, themePref(store).get())
    }

    @Test
    fun `glass style defaults to Frosted when nothing is stored`() = withStore { store ->
        assertEquals(OruGlassStyle.Frosted, glassPref(store).get())
    }

    @Test
    fun `a chosen theme mode is stored by its name and read back`() = withStore { store ->
        themePref(store).update(OruThemeMode.Dark)

        assertEquals("Dark", store.data.first()[stringPreferencesKey(themeKey)])
        assertEquals(OruThemeMode.Dark, themePref(store).get())
    }

    @Test
    fun `a chosen glass style is stored by its name and read back`() = withStore { store ->
        glassPref(store).update(OruGlassStyle.Clear)

        assertEquals("Clear", store.data.first()[stringPreferencesKey(glassKey)])
        assertEquals(OruGlassStyle.Clear, glassPref(store).get())
    }

    @Test
    fun `an unknown stored theme mode falls back to the default`() = withStore { store ->
        store.edit { it[stringPreferencesKey(themeKey)] = "Sepia" }

        assertEquals(OruThemeMode.System, themePref(store).get())
    }

    @Test
    fun `an unknown stored glass style falls back to the default`() = withStore { store ->
        store.edit { it[stringPreferencesKey(glassKey)] = "Opaque" }

        assertEquals(OruGlassStyle.Frosted, glassPref(store).get())
    }
}
