/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.nav

import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import app.morphe.manager.ui.model.navigation.Patcher
import kotlinx.serialization.Serializable

/** Key under which a Focus entry keeps the [Patcher.ViewModelParams] of its run, as the Patcher route does. */
const val ORU_FOCUS_ARGS = "args"

/**
 * Focus on one home card.
 *
 * @param packageKey The card's identity, which is `HomeAppItem.id`: the package of that install.
 *   Every clone has a package of its own, so the key tells the cards of one app apart.
 */
@Serializable
data class OruFocus(val packageKey: String)

/** Shared element key of a card and the Focus hero that it morphs into. */
fun oruTileSharedKey(homeAppId: String): String = "tile/$homeAppId"

/** Shared element key of a card's app name and the Focus title. Its own prefix, so it never meets the tile key. */
fun oruTitleSharedKey(homeAppId: String): String = "title/$homeAppId"

/**
 * Shared element key of a card's primary button and the Focus dock's primary button. The pill travels from the card
 * into the dock and reshapes on the way.
 */
fun oruCtaSharedKey(homeAppId: String): String = "cta/$homeAppId"

/**
 * Starts a patch run inside the Focus entry for the card it belongs to. The params travel in that
 * entry's saved state, the same way the Patcher route carries them, so they survive a process
 * death with the back stack.
 *
 * If Focus is already on top, that entry is reused rather than pushed a second time.
 */
fun NavController.startOruRun(params: Patcher.ViewModelParams) {
    val focused = currentBackStackEntry?.takeIf { it.destination.hasRoute<OruFocus>() }
    val packageKey = focused?.toRoute<OruFocus>()?.packageKey
        ?: params.targetPackageName
        ?: params.selectedApp.packageName
    val route = OruFocus(packageKey)
    navigate(route) { launchSingleTop = true }
    getBackStackEntry(route).savedStateHandle[ORU_FOCUS_ARGS] = params
    // A new start lifts the gate that kept the run lost to process death out of this Focus
    runCatching {
        org.koin.core.context.GlobalContext.get()
            .getOrNull<app.morphe.manager.ui.oru.focus.SingleRunOwner>()
            ?.clearStoppedRun(packageKey)
    }
}

/**
 * Brings the Focus of [packageKey] back on top, with its entry and so its run. A plain navigate would push a second
 * Focus with a new view model, which has no run. Opens the Focus only when its entry is no longer on the stack.
 */
fun NavController.returnToOruFocus(packageKey: String) {
    val route = OruFocus(packageKey)
    if (!popBackStack(route, false)) navigate(route) { launchSingleTop = true }
}
