/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus

import androidx.compose.runtime.Composable
import app.morphe.manager.ui.screen.patcher.UnusableOptionPathsDialog
import app.morphe.manager.ui.screen.settings.system.PrePatchInstallerDialog
import app.morphe.manager.ui.viewmodel.HomeViewModel

/**
 * The patch-selection and root-mode dialogs of the old Home (`HomeDialogs.kt:370-460`, the pre-patch step of
 * `HomeScreen.kt:259-266`). The Focus mounts them itself: the Home's dialog host is not composed while the Focus is
 * mounted (`HomeViewModel.oruFocusMounted`), and a selection left without its dialog would hold the flow with
 * nothing to answer.
 */
@Composable
internal fun OruSelectionDialogs(home: HomeViewModel) {
    // The source is answered by the Focus itself (the source choice), never by a dialog before it: see OruFocusViewModel

    // The expert selection is not drawn here: the Oru patch sheet (OruExpertSheets) answers it in the Focus

    // A selection whose option paths cannot be read is answered here, before the run would fail on them
    home.expertModeUnreadablePaths.takeIf { it.isNotEmpty() }?.let { failures ->
        UnusableOptionPathsDialog(
            failures = failures,
            onRetryAfterPermission = { home.proceedExpertMode() },
            canClearPaths = true,
            onClearPaths = { home.clearExpertModeUnreadablePaths() },
            onDismiss = { home.dismissExpertModeUnreadablePaths() },
        )
    }

    if (home.showPrePatchInstallerDialog) {
        PrePatchInstallerDialog(
            packageName = home.pendingPackageName,
            onSelectMount = { home.resolvePrePatchInstallerChoice(useMount = true) },
            onSelectStandard = { home.resolvePrePatchInstallerChoice(useMount = false) },
            onDismiss = home::dismissPrePatchInstallerDialog,
        )
    }
}
