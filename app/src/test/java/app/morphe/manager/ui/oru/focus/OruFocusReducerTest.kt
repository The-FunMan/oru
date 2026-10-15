/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus

import app.morphe.manager.ui.oru.components.OruTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OruFocusReducerTest {

    private fun reduce(flags: OruFocusFlags) = OruFocusReducer.reduce(flags)

    private fun noticeOf(state: OruFocusState): OruFocusNotice = assertIs<OruFocusState.Notice>(state).notice

    @Test
    fun `nothing in flight asks for the APK choice`() {
        assertEquals(OruFocusState.Choose(OruChooseStage.Pick, emptyList()), reduce(OruFocusFlags()))
    }

    @Test
    fun `the APK choice is open`() {
        val state = reduce(OruFocusFlags(pendingForApp = true, pickOpen = true))
        assertEquals(OruFocusState.Choose(OruChooseStage.Pick, emptyList()), state)
    }

    @Test
    fun `the download step is open`() {
        val state = reduce(OruFocusFlags(pendingForApp = true, downloadOpen = true))
        assertEquals(OruFocusState.Choose(OruChooseStage.Download, emptyList()), state)
    }

    @Test
    fun `waiting for the file after the hand-off`() {
        assertEquals(OruFocusState.WaitingForFile, reduce(OruFocusFlags(pendingForApp = true, waitingForFile = true)))
    }

    @Test
    fun `a file being checked shows the checking state`() {
        assertEquals(OruFocusState.Checking, reduce(OruFocusFlags(processing = true, pickOpen = true)))
    }

    @Test
    fun `wrong package blocks and offers another file`() {
        val notice = noticeOf(reduce(OruFocusFlags(wrongPackage = "com.other")))
        assertEquals(OruNoticeKind.WrongPackage, notice.kind)
        assertEquals(listOf(OruFocusAction.UseAnother), notice.actions)
        assertTrue(notice.blocking)
    }

    @Test
    fun `wrong package wins over a split archive`() {
        val notice = noticeOf(reduce(OruFocusFlags(wrongPackage = "com.other", splitApk = true)))
        assertEquals(OruNoticeKind.WrongPackage, notice.kind)
    }

    @Test
    fun `a split archive offers the merge and another file`() {
        val notice = noticeOf(reduce(OruFocusFlags(splitApk = true, invalidSignature = true)))
        assertEquals(OruNoticeKind.Split, notice.kind)
        assertEquals(listOf(OruFocusAction.ProceedSplit, OruFocusAction.UseAnother), notice.actions)
    }

    @Test
    fun `an APK signature that does not match the bundle offers patch anyway`() {
        val notice = noticeOf(reduce(OruFocusFlags(invalidSignature = true)))
        assertEquals(OruNoticeKind.InvalidSignature, notice.kind)
        assertEquals(listOf(OruFocusAction.PatchAnyway, OruFocusAction.UseAnother), notice.actions)
    }

    @Test
    fun `an unsupported version offers a picked version or patching anyway with confirm`() {
        val notice = noticeOf(reduce(OruFocusFlags(unsupportedVersion = "19.0")))
        assertEquals(OruNoticeKind.Unsupported, notice.kind)
        assertEquals("19.0", notice.version)
        assertEquals(listOf(OruFocusAction.PickVersion, OruFocusAction.PatchAnywayUnsupported), notice.actions)
    }

    @Test
    fun `an experimental version with the switch off offers to show experimental versions`() {
        val notice = noticeOf(reduce(OruFocusFlags(unsupportedVersion = "21.41", unsupportedIsExperimental = true)))
        assertEquals(OruNoticeKind.ExperimentalOff, notice.kind)
        assertEquals(listOf(OruFocusAction.ShowExperimental, OruFocusAction.PatchAnywayUnsupported), notice.actions)
    }

    @Test
    fun `an experimental version with the switch on never offers show experimental versions`() {
        val notice = noticeOf(reduce(OruFocusFlags(experimentalVersion = "21.41")))
        assertEquals(OruNoticeKind.ExperimentalOn, notice.kind)
        assertEquals(listOf(OruFocusAction.PickVersion, OruFocusAction.ProceedExperimental), notice.actions)
        assertFalse(notice.actions.contains(OruFocusAction.ShowExperimental))
    }

    @Test
    fun `no compatible versions is a dead end with dismiss`() {
        val notice = noticeOf(reduce(OruFocusFlags(noCompatibleVersions = true)))
        assertEquals(OruNoticeKind.NoCompatible, notice.kind)
        assertEquals(listOf(OruFocusAction.Dismiss), notice.actions)
    }

    @Test
    fun `an open selection wins over the APK choice`() {
        assertEquals(OruFocusState.Selecting, reduce(OruFocusFlags(selecting = true, pickOpen = true)))
    }

    @Test
    fun `a held run with nothing to answer is ready to start`() {
        val state = assertIs<OruFocusState.Ready>(reduce(OruFocusFlags(held = true, apkBytes = 100, freeBytes = 1_000)))
        assertTrue(state.canStart)
        assertTrue(state.notices.isEmpty())
    }

    @Test
    fun `the selection answers itself only when the user did not ask and patches are selected`() {
        assertTrue(OruFocusReducer.autoSelects(userInitiated = false, selectedCount = 3))
        assertFalse(OruFocusReducer.autoSelects(userInitiated = true, selectedCount = 3))
        assertFalse(OruFocusReducer.autoSelects(userInitiated = false, selectedCount = 0))
        assertFalse(OruFocusReducer.autoSelects(userInitiated = true, selectedCount = 0))
    }

    @Test
    fun `storage below twice the APK blocks the start`() {
        val state = assertIs<OruFocusState.Ready>(reduce(OruFocusFlags(held = true, apkBytes = 100, freeBytes = 199)))
        assertFalse(state.canStart)
        val low = state.notices.single()
        assertEquals(OruNoticeKind.LowStorage, low.kind)
        assertTrue(low.blocking)
        assertEquals(199L, low.freeBytes)
    }

    @Test
    fun `storage at exactly twice the APK does not block`() {
        val state = assertIs<OruFocusState.Ready>(reduce(OruFocusFlags(held = true, apkBytes = 100, freeBytes = 200)))
        assertTrue(state.canStart)
    }

    @Test
    fun `unknown storage or APK size never blocks the start`() {
        assertTrue(assertIs<OruFocusState.Ready>(reduce(OruFocusFlags(held = true, apkBytes = null, freeBytes = 1))).canStart)
        assertTrue(assertIs<OruFocusState.Ready>(reduce(OruFocusFlags(held = true, apkBytes = 100, freeBytes = null))).canStart)
    }

    @Test
    fun `metered data is a banner that never blocks`() {
        val choose = assertIs<OruFocusState.Choose>(reduce(OruFocusFlags(pickOpen = true, metered = true)))
        assertEquals(OruNoticeKind.Metered, choose.notices.single().kind)
        assertFalse(choose.notices.single().blocking)
        assertEquals(OruTone.Neutral, choose.notices.single().tone)

        val ready = assertIs<OruFocusState.Ready>(reduce(OruFocusFlags(held = true, metered = true)))
        assertTrue(ready.canStart)
        assertEquals(OruNoticeKind.Metered, ready.notices.single().kind)
    }

    @Test
    fun `a shown run owns the Focus whatever the flow holds`() {
        assertEquals(
            OruFocusState.Run(OruRunStatus.Working),
            reduce(OruFocusFlags(pickOpen = true, held = true, run = OruRunStatus.Working)),
        )
        assertEquals(
            OruFocusState.Run(OruRunStatus.Done),
            reduce(OruFocusFlags(processing = true, run = OruRunStatus.Done)),
        )
    }

    @Test
    fun `a failed or cancelled run is reported as the run`() {
        assertEquals(OruFocusState.Run(OruRunStatus.Failed), reduce(OruFocusFlags(run = OruRunStatus.Failed)))
        assertEquals(OruFocusState.Run(OruRunStatus.Cancelled), reduce(OruFocusFlags(run = OruRunStatus.Cancelled)))
    }

    @Test
    fun `an unsupported version points to the version its source can patch at`() {
        val notice = noticeOf(
            reduce(OruFocusFlags(unsupportedVersion = "21.18.164", supportedVersion = "21.16.256")),
        )
        assertEquals(OruNoticeKind.Unsupported, notice.kind)
        assertEquals("21.16.256", notice.supportedVersion)
    }

    @Test
    fun `an unsupported version without a source version carries no version to point to`() {
        val notice = noticeOf(reduce(OruFocusFlags(unsupportedVersion = "21.18.164")))
        assertEquals(OruNoticeKind.Unsupported, notice.kind)
        assertEquals(null, notice.supportedVersion)
    }

    @Test
    fun `simple mode offers find on apkmirror and back for a version the app cannot be patched at`() {
        val notice = noticeOf(
            reduce(OruFocusFlags(unsupportedVersion = "21.16.251", supportedVersion = "21.20.405", expert = false)),
        )
        assertEquals(OruNoticeKind.Unsupported, notice.kind)
        assertEquals(listOf(OruFocusAction.FindSupported, OruFocusAction.Back), notice.actions)
        assertEquals("21.20.405", notice.supportedVersion)
        assertTrue(notice.blocking)
    }

    @Test
    fun `simple mode never offers patch anyway, for an unsupported or an experimental version`() {
        val unsupported = noticeOf(reduce(OruFocusFlags(unsupportedVersion = "1", expert = false)))
        assertFalse(unsupported.actions.contains(OruFocusAction.PatchAnywayUnsupported))
        val experimentalOff = noticeOf(
            reduce(OruFocusFlags(unsupportedVersion = "1", unsupportedIsExperimental = true, expert = false)),
        )
        assertEquals(OruNoticeKind.Unsupported, experimentalOff.kind)
        assertFalse(experimentalOff.actions.contains(OruFocusAction.PatchAnywayUnsupported))
        assertFalse(experimentalOff.actions.contains(OruFocusAction.ShowExperimental))
    }

    @Test
    fun `simple mode keeps an experimental version that the switch allows behind continue and back`() {
        val notice = noticeOf(reduce(OruFocusFlags(experimentalVersion = "2.0", expert = false)))
        assertEquals(OruNoticeKind.ExperimentalOn, notice.kind)
        assertEquals(listOf(OruFocusAction.ProceedExperimental, OruFocusAction.Back), notice.actions)
    }

    @Test
    fun `expert mode keeps pick another version and patch anyway for an unsupported version`() {
        val notice = noticeOf(reduce(OruFocusFlags(unsupportedVersion = "1", supportedVersion = "2", expert = true)))
        assertEquals(listOf(OruFocusAction.PickVersion, OruFocusAction.PatchAnywayUnsupported), notice.actions)
    }
}
