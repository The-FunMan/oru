/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.run

/**
 * Which view of a patch run is on screen, decided from the run's hero state. The Focus host switches on
 * this and draws the matching composable:
 * - [Idle]: nothing ran yet, the Focus choose or ready view shows instead.
 * - [Working] and [Done]: [OruRunContent].
 * - [Failed] and [Cancelled]: [OruFailedContent].
 */
sealed interface OruRunScreenState {
    data object Idle : OruRunScreenState

    data object Working : OruRunScreenState

    data object Done : OruRunScreenState

    /** [offerMemoryRetry] is the data layer's decision that a memory offer applies, see [OruRunStateUi]. */
    data class Failed(val offerMemoryRetry: Boolean) : OruRunScreenState

    data object Cancelled : OruRunScreenState
}

/** The view a run state shows. Pure, so the Focus host only switches on the result. */
fun oruRunScreenStateOf(run: OruRunStateUi): OruRunScreenState = when (run.hero) {
    OruHeroState.READY -> OruRunScreenState.Idle
    OruHeroState.WORKING -> OruRunScreenState.Working
    OruHeroState.DONE -> OruRunScreenState.Done
    OruHeroState.FAILED -> OruRunScreenState.Failed(offerMemoryRetry = run.offerMemoryRetry)
    OruHeroState.CANCELLED -> OruRunScreenState.Cancelled
}
