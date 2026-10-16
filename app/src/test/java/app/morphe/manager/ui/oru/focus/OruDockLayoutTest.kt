/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus

import app.morphe.manager.ui.oru.components.OruTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OruDockLayoutTest {

    private fun plan(
        state: OruFocusState,
        pending: Boolean = true,
        hasSaved: Boolean = false,
        hasInstalled: Boolean = false,
    ) = oruDockPlan(state, pending = pending, hasSaved = hasSaved, hasInstalled = hasInstalled)

    private val pick = OruFocusState.Choose(OruChooseStage.Pick, emptyList())
    private val download = OruFocusState.Choose(OruChooseStage.Download, emptyList())

    @Test
    fun `a saved copy is the primary action when the phone has one`() {
        assertEquals(
            DockPlan(DockAction.UseSavedCopy, listOf(DockAction.FindOnApkMirror, DockAction.UseFile)),
            plan(pick, hasSaved = true),
        )
    }

    @Test
    fun `the saved copy comes before the installed app`() {
        assertEquals(
            DockPlan(DockAction.UseSavedCopy, listOf(DockAction.FindOnApkMirror, DockAction.UseFile)),
            plan(pick, hasSaved = true, hasInstalled = true),
        )
    }

    @Test
    fun `the installed app is the primary action when there is no saved copy`() {
        assertEquals(
            DockPlan(DockAction.UseInstalledCopy, listOf(DockAction.FindOnApkMirror, DockAction.UseFile)),
            plan(pick, hasInstalled = true),
        )
    }

    @Test
    fun `finding the APK is the primary action when the phone has no copy`() {
        assertEquals(DockPlan(DockAction.FindOnApkMirror, listOf(DockAction.UseFile)), plan(pick))
    }

    @Test
    fun `get APK is alone when no flow is pending`() {
        assertEquals(DockPlan(DockAction.GetApk), plan(pick, pending = false, hasSaved = true))
    }

    @Test
    fun `the download step opens APKMirror with Back beside it`() {
        assertEquals(DockPlan(DockAction.OpenApkMirror, listOf(DockAction.Back)), plan(download))
    }

    @Test
    fun `waiting for the file asks for the file with a reopen beside it`() {
        assertEquals(
            DockPlan(DockAction.ChooseFile, listOf(DockAction.OpenAgain)),
            plan(OruFocusState.WaitingForFile),
        )
    }

    @Test
    fun `the ready state has only the start action`() {
        val ready = OruFocusState.Ready(notices = emptyList(), canStart = false)
        assertEquals(DockPlan(DockAction.StartRun), plan(ready))
    }

    @Test
    fun `states that answer through notices or run have no dock`() {
        val notice = OruFocusNotice(kind = OruNoticeKind.Split, tone = OruTone.Warn, actions = emptyList())
        val states = listOf(
            OruFocusState.Checking,
            OruFocusState.Notice(notice),
            OruFocusState.Selecting,
            OruFocusState.Run(OruRunStatus.Working),
        )
        states.forEach { assertNull(plan(it), "$it has no dock") }
    }

    @Test
    fun `every dock holds at most two secondaries and never repeats the primary`() {
        val ready = OruFocusState.Ready(emptyList(), canStart = true)
        val states = listOf(pick, download, OruFocusState.WaitingForFile, ready)
        for (state in states) {
            for (pending in listOf(true, false)) {
                for (saved in listOf(true, false)) {
                    for (installed in listOf(true, false)) {
                        val dock = plan(state, pending = pending, hasSaved = saved, hasInstalled = installed)
                        assertTrue(dock != null, "$state has a dock")
                        assertTrue(dock.secondaries.size <= MaxDockSecondaries)
                        assertTrue(dock.primary !in dock.secondaries)
                        assertEquals(dock.secondaries.distinct(), dock.secondaries)
                    }
                }
            }
        }
    }

    @Test
    fun `a dock with three secondaries is refused`() {
        assertFailsWith<IllegalArgumentException> {
            DockPlan(
                DockAction.StartRun,
                listOf(DockAction.UseFile, DockAction.Back, DockAction.OpenAgain),
            )
        }
    }

    @Test
    fun `the primary action cannot be a secondary too`() {
        assertFailsWith<IllegalArgumentException> {
            DockPlan(DockAction.UseFile, listOf(DockAction.UseFile))
        }
    }
}
