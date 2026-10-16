/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.nav

import app.morphe.manager.ui.oru.home.OruHomeAction
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OruCardActionRoutingTest {
    @Test
    fun `app info and open stay on home`() {
        assertFalse(oruActionOpensFocus(OruHomeAction.AppInfo))
        assertFalse(oruActionOpensFocus(OruHomeAction.Open))
    }

    @Test
    fun `every patch step opens focus`() {
        listOf(
            OruHomeAction.CheckAndPatch, OruHomeAction.FindOnApkMirror, OruHomeAction.UseFile,
            OruHomeAction.PatchFromSaved, OruHomeAction.UseAnotherFile, OruHomeAction.GetIt,
            OruHomeAction.Patch, OruHomeAction.Update, OruHomeAction.PatchAgain,
            OruHomeAction.ChooseFile, OruHomeAction.OpenApkMirrorAgain, OruHomeAction.Install,
        ).forEach { assertTrue(oruActionOpensFocus(it), it.name) }
    }
}
