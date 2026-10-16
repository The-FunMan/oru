/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus

import app.morphe.manager.ui.oru.components.OruTone

/**
 * Maps the flags of the old flow to one [OruFocusState]. Pure: the table tests drive it directly.
 *
 * Precedence, first match wins (the order the old checks ran in, so a file that fails two checks reports
 * the one the old flow reported). Line numbers are HomeViewModel.kt at the time of writing:
 * 1. A run that is shown ([OruFocusFlags.run]) owns the Focus.
 * 2. A file being checked: `HomeViewModel.processingApkSelection` (`HomeViewModel.kt:1668-1695`).
 * 3. Wrong package (`HomeViewModel.kt:1764`, `showWrongPackageDialog`, inside `processSelectedApp` at 1758).
 * 4. Split archive where a full APK is required (`HomeViewModel.kt:1786`, `showSplitApkWarningDialog`).
 * 5. APK signature against the bundle (`HomeViewModel.kt:1808`, `showInvalidSignatureDialog`).
 * 6. Unsupported version with the "patch anyway" confirm (`HomeViewModel.kt:1870`, `showUnsupportedVersionDialog`).
 *    An experimental version while its switch is off takes this path too, with the switch copy (see 7).
 * 7. Experimental version while the switch is on (`HomeViewModel.kt:1882`, `showExperimentalVersionDialog`).
 *    The copy follows the switch, so the action is "Show experimental versions" only when the switch is off (C10, P-dev4).
 * 8. No compatible versions (`HomeViewModel.kt:1317`, `showNoCompatibleVersionsDialog`, set in `showPatchDialogInternal`).
 * 9. Patch selection or root mode open (`showExpertModeDialog`, `showPrePatchInstallerDialog`).
 * 10. The APK choice (`showApkAvailabilityDialog`), then the download step (`showDownloadInstructionsDialog`),
 *     then the wait for the file (`showFilePickerPromptDialog`, `HomeViewModel.kt:2678-2690`).
 * 11. A held run: the flow finished and `proceedWithPatching` handed the params over (`HomeViewModel.kt:2214-2247`).
 *     Start is blocked only when storage cannot hold the output (see [READY_STORAGE_FACTOR]). Metered data is a banner
 *     only (`guardPatching`, `HomeViewModel.kt:784-806`, D18).
 * 12. Nothing in flight: the APK choice again, so the user can pick a file.
 *
 * Gates `guardPatching` raises for metered data and low disk are not states here: the view model proceeds past them
 * (D18), so they never reach the reducer as a question.
 */
object OruFocusReducer {

    /**
     * The output of a patch run is written beside the input and the signed copy is made from it, so the free
     * space the run needs is about twice the APK. Below that the run cannot finish, so the start is blocked (D18).
     */
    const val READY_STORAGE_FACTOR = 2

    /**
     * Whether the patch selection is answered with the defaults instead of the sheet: only when the user did not
     * ask to change the patches and the flow already selected some.
     */
    fun autoSelects(userInitiated: Boolean, selectedCount: Int): Boolean = !userInitiated && selectedCount > 0

    fun reduce(flags: OruFocusFlags): OruFocusState {
        flags.run?.let { return OruFocusState.Run(it) }
        if (flags.processing) return OruFocusState.Checking

        flags.wrongPackage?.let {
            return validation(OruNoticeKind.WrongPackage, OruTone.Error, listOf(OruFocusAction.UseAnother), blocking = true)
        }
        if (flags.splitApk) {
            return validation(
                OruNoticeKind.Split,
                OruTone.Warn,
                listOf(OruFocusAction.ProceedSplit, OruFocusAction.UseAnother),
                blocking = true,
            )
        }
        if (flags.invalidSignature) {
            return validation(
                OruNoticeKind.InvalidSignature,
                OruTone.Error,
                listOf(OruFocusAction.PatchAnyway, OruFocusAction.UseAnother),
                blocking = true,
            )
        }
        flags.unsupportedVersion?.let { version ->
            // Simple points to the version the source can patch at and offers no patch-anyway; an experimental version
            // that is off is an unsupported one to Simple, as it is in the notice's copy
            if (!flags.expert) {
                return validation(
                    OruNoticeKind.Unsupported,
                    OruTone.Warn,
                    listOf(OruFocusAction.FindSupported, OruFocusAction.Back),
                    blocking = true,
                    version = version,
                    supportedVersion = flags.supportedVersion,
                )
            }
            return if (flags.unsupportedIsExperimental) {
                validation(
                    OruNoticeKind.ExperimentalOff,
                    OruTone.Warn,
                    listOf(OruFocusAction.ShowExperimental, OruFocusAction.PatchAnywayUnsupported),
                    blocking = true,
                    version = version,
                )
            } else {
                validation(
                    OruNoticeKind.Unsupported,
                    OruTone.Warn,
                    listOf(OruFocusAction.PickVersion, OruFocusAction.PatchAnywayUnsupported),
                    blocking = true,
                    version = version,
                    supportedVersion = flags.supportedVersion,
                )
            }
        }
        flags.experimentalVersion?.let { version ->
            return validation(
                OruNoticeKind.ExperimentalOn,
                OruTone.Warn,
                if (flags.expert) {
                    listOf(OruFocusAction.PickVersion, OruFocusAction.ProceedExperimental)
                } else {
                    listOf(OruFocusAction.ProceedExperimental, OruFocusAction.Back)
                },
                blocking = true,
                version = version,
            )
        }
        if (flags.noCompatibleVersions) {
            return validation(OruNoticeKind.NoCompatible, OruTone.Error, listOf(OruFocusAction.Dismiss), blocking = true)
        }
        if (flags.selecting) return OruFocusState.Selecting
        if (flags.pickOpen) return OruFocusState.Choose(OruChooseStage.Pick, banners(flags))
        if (flags.downloadOpen) return OruFocusState.Choose(OruChooseStage.Download, banners(flags))
        if (flags.waitingForFile) return OruFocusState.WaitingForFile
        if (flags.held) return ready(flags)
        return OruFocusState.Choose(OruChooseStage.Pick, banners(flags))
    }

    /** The start check and the notices of a held run. */
    private fun ready(flags: OruFocusFlags): OruFocusState.Ready {
        val apk = flags.apkBytes
        val free = flags.freeBytes
        val storageBlocks = apk != null && free != null && free < apk * READY_STORAGE_FACTOR
        val notices = buildList {
            if (storageBlocks) {
                add(
                    OruFocusNotice(
                        kind = OruNoticeKind.LowStorage,
                        tone = OruTone.Error,
                        actions = emptyList(),
                        blocking = true,
                        freeBytes = free,
                        neededBytes = apk!! * READY_STORAGE_FACTOR - free!!,
                    ),
                )
            }
            if (flags.metered) add(metered())
        }
        return OruFocusState.Ready(notices = notices, canStart = !storageBlocks)
    }

    /** Notices that do not stop the APK choice: metered data only. */
    private fun banners(flags: OruFocusFlags): List<OruFocusNotice> = if (flags.metered) listOf(metered()) else emptyList()

    private fun metered() = OruFocusNotice(OruNoticeKind.Metered, OruTone.Neutral, emptyList())

    private fun validation(
        kind: OruNoticeKind,
        tone: OruTone,
        actions: List<OruFocusAction>,
        blocking: Boolean,
        version: String? = null,
        supportedVersion: String? = null,
    ): OruFocusState.Notice = OruFocusState.Notice(
        OruFocusNotice(
            kind = kind,
            tone = tone,
            actions = actions,
            blocking = blocking,
            version = version,
            supportedVersion = supportedVersion,
        ),
    )
}
