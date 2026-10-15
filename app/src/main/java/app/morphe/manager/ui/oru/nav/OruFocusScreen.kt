/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.nav

import androidx.compose.runtime.Composable
import app.morphe.manager.ui.oru.focus.OruFocusRoute
import app.morphe.manager.ui.model.navigation.Patcher

/**
 * The Focus destination's content: the card's hero, the APK flow, the notices, the run and the install.
 *
 * @param packageKey the card's id (`HomeAppItem.id`), which is also the shared tile key of the hero.
 * @param run the run parameters the entry holds once the user started a run; null before that.
 * @param onStartRun starts the run in this entry (`NavController.startOruRun`).
 */
@Composable
fun OruFocusScreen(
    packageKey: String,
    run: Patcher.ViewModelParams?,
    onBack: () -> Unit,
    onStartRun: (Patcher.ViewModelParams) -> Unit = {},
) {
    OruFocusRoute(
        packageKey = packageKey,
        run = run,
        onBack = onBack,
        onStartRun = onStartRun,
    )
}
