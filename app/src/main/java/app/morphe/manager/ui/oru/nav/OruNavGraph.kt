/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.nav

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.morphe.manager.ui.oru.focus.SingleRunOwner
import app.morphe.manager.ui.model.navigation.Patcher
import org.koin.compose.koinInject

/**
 * The oru-only destinations. The home destination itself stays `HomeScreen` in MainActivity,
 * which renders [OruHomeScreen] for oru.
 */
fun NavGraphBuilder.oruDestinations(navController: NavController) {
    composable<OruFocus> { entry ->
        val route = entry.toRoute<OruFocus>()
        val singleRunOwner: SingleRunOwner = koinInject()
        val activeRun by singleRunOwner.currentRun.collectAsStateWithLifecycle()
        // Written by NavController.startOruRun after the entry exists, so it is observed, not read once
        val savedRun by entry.savedStateHandle
            .getStateFlow<Patcher.ViewModelParams?>(ORU_FOCUS_ARGS, null)
            .collectAsStateWithLifecycle()

        // The Focus of the run the process died in opens on its Start, not on the run that was lost. Only that Focus
        // is gated, and the gate lifts when it starts again (SingleRunOwner.clearStoppedRun), so nothing is written here
        val stoppedKey by singleRunOwner.stoppedPackageKey.collectAsStateWithLifecycle()
        val run = if (route.packageKey == stoppedKey) {
            null
        } else {
            savedRun ?: activeRun?.takeIf { it.packageKey == route.packageKey }?.params
        }

        OruDestinationScope(animatedVisibilityScope = this) {
            OruFocusScreen(
                packageKey = route.packageKey,
                run = run,
                onBack = { navController.popBackStack() },
                onStartRun = { params -> navController.startOruRun(params) },
            )
        }
    }
}
