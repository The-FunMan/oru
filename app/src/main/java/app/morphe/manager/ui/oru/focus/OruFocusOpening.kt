/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus

import app.morphe.manager.ui.oru.home.OruHomeAction

/**
 * The step a Focus opens on, decided by the primary action of the card it was opened from. A card that says
 * "Get it" opens on the APKMirror step, and "Patch from saved copy" lands on the ready state. Nothing starts by
 * itself: the browser opens on the user's tap, and the run waits for the Patch tap.
 */
enum class OruFocusOpening {
    /** The APK choice, where the user picks how the file is got. */
    Choice,

    /** The APKMirror step, with its link resolved. The browser still opens only when the user taps Open APKMirror. */
    DownloadStep,

    /** The saved copy is picked, so the Focus is ready to patch with the Patch button. */
    SavedCopy;

    companion object {
        fun forAction(action: OruHomeAction): OruFocusOpening = when (action) {
            OruHomeAction.GetIt -> DownloadStep
            OruHomeAction.PatchFromSaved -> SavedCopy
            else -> Choice
        }
    }
}
