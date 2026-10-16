/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.model

import app.morphe.manager.patcher.logger.LogLevel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileLineVisibilityTest {
    @Test
    fun `phase profile lines stay out of the user visible log`() {
        assertFalse(parseRunLine(LogLevel.INFO, "oru.profile phase=resource decode wall=2.14s").showInLog)
        assertFalse(parseRunLine(LogLevel.INFO, "oru.profile.json {}").showInLog)
    }

    @Test
    fun `ordinary patch lines still show`() {
        assertTrue(parseRunLine(LogLevel.INFO, "Executing patches").showInLog)
    }
}
