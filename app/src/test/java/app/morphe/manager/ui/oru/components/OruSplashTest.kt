/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OruSplashTest {
    private val main = "android.intent.action.MAIN"
    private val launcher = setOf("android.intent.category.LAUNCHER")

    @Test
    fun `the plain launcher start shows the splash`() {
        assertTrue(isPlainLauncherStart(main, launcher, hasData = false, hasPurposeExtra = false))
    }

    @Test
    fun `a start without the launcher category does not`() {
        assertFalse(isPlainLauncherStart(main, emptySet(), hasData = false, hasPurposeExtra = false))
    }

    @Test
    fun `a share, a batch request, a deep link or a view do not`() {
        assertFalse(isPlainLauncherStart("android.intent.action.SEND", launcher, false, false))
        assertFalse(isPlainLauncherStart("app.morphe.manager.action.BATCH_PATCH", launcher, false, false))
        assertFalse(isPlainLauncherStart("android.intent.action.VIEW", launcher, true, false))
        assertFalse(isPlainLauncherStart(main, launcher, hasData = true, hasPurposeExtra = false))
    }

    @Test
    fun `a shortcut or a notification with its own action or extra does not`() {
        assertFalse(isPlainLauncherStart("app.morphe.manager.action.CHECK_UPDATES", launcher, false, false))
        assertFalse(isPlainLauncherStart(main, launcher, hasData = false, hasPurposeExtra = true))
    }

    @Test
    fun `the splash is claimed once per process`() {
        assertTrue(OruSplashGate.claimProcessStart())
        assertFalse(OruSplashGate.claimProcessStart())
    }
}
