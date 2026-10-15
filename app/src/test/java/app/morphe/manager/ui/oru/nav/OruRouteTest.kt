/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.nav

import androidx.lifecycle.SavedStateHandle
import app.morphe.manager.ui.model.SelectedApp
import app.morphe.manager.ui.model.navigation.Patcher
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OruRouteTest {
    private val json = Json

    @Test
    fun focusRoundTripsThroughSerialization() {
        val route = OruFocus(packageKey = "com.example.app.clone1")
        val encoded = json.encodeToString(OruFocus.serializer(), route)

        assertEquals(route, json.decodeFromString(OruFocus.serializer(), encoded))
    }

    @Test
    fun runArgsAreAbsentUntilARunStarts() {
        val handle = SavedStateHandle()

        assertNull(handle.getStateFlow<Patcher.ViewModelParams?>(ORU_FOCUS_ARGS, null).value)
    }

    @Test
    fun runArgsSurviveInTheFocusSavedState() {
        val params = Patcher.ViewModelParams(
            selectedApp = SelectedApp.Installed(packageName = "com.example.app", version = "1.0"),
            selectedPatches = emptyMap(),
            options = emptyMap()
        )
        val handle = SavedStateHandle()

        // Same slot the Patcher route reads its params from, so both paths restore alike
        handle[ORU_FOCUS_ARGS] = params

        assertEquals("args", ORU_FOCUS_ARGS)
        assertEquals(params, handle.get<Patcher.ViewModelParams>(ORU_FOCUS_ARGS))
        assertEquals(
            params,
            handle.getStateFlow<Patcher.ViewModelParams?>(ORU_FOCUS_ARGS, null).value
        )
    }
}
