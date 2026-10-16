/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.home

/** The three steps of the first-patch tour, in the order a new user takes them. */
enum class OruTourStep {
    Find,
    Patch,
    Install;

    /** The step after this one. The last step stays the last. */
    fun next(): OruTourStep = OruTourStep.entries.getOrElse(ordinal + 1) { this }
}

/**
 * What the tour reads from Home: whether the list is still loading, the status of every card, and whether a run is
 * going. Built by [oruTourInputOf] from the Home state, so the tour and the cards never disagree.
 */
data class OruTourInput(
    val loading: Boolean,
    val statuses: List<OruHomeCardStatus>,
    val runActive: Boolean,
)

/** The tour's own state: the step it highlights, and whether the user has dismissed it in this session. */
data class OruTourState(
    val step: OruTourStep = OruTourStep.Find,
    val dismissed: Boolean = false,
)

/**
 * A card that means the user already patched or installed something, so a first-run tour has nothing to teach. An update
 * badge is not one of them: a stock app that is merely on the phone can carry it.
 */
private val PatchedStatuses = setOf(
    OruHomeCardStatus.Installed,
    OruHomeCardStatus.PatchedElsewhere,
)

/** A patched copy kept from an earlier run. It also means the user has already patched something. */
private val SavedStatuses = setOf(
    OruHomeCardStatus.SavedCopy,
    OruHomeCardStatus.SavedCopyGated,
)

fun oruTourInputOf(state: OruHomeUiState, runActive: Boolean): OruTourInput = OruTourInput(
    loading = state.loading,
    statuses = state.cards.map { it.status },
    runActive = runActive,
)

/**
 * Whether the tour is on screen. It is a first-run tour: it shows only while the list is loaded and has cards, when no
 * card is patched or installed and no patched copy is saved, and when neither the stored flag nor the user's dismissal
 * says it is done. A finished run that waits for its install still shows the tour, which is its step 3.
 */
fun oruTourVisible(done: Boolean, state: OruTourState, input: OruTourInput): Boolean =
    !done &&
        !state.dismissed &&
        !input.loading &&
        input.statuses.isNotEmpty() &&
        input.statuses.none { it in PatchedStatuses || it in SavedStatuses }

/** Whether a card shows that the user's first patch is on the phone. The tour then ends for good. */
fun oruTourPatchedOn(statuses: List<OruHomeCardStatus>): Boolean = statuses.any { it in PatchedStatuses }

/** The step the Home state points at: the install when a run waits for it, the patch while a run goes, else the find. */
fun oruTourDerivedStep(input: OruTourInput): OruTourStep = when {
    OruHomeCardStatus.ReadyToInstall in input.statuses -> OruTourStep.Install
    input.runActive -> OruTourStep.Patch
    else -> OruTourStep.Find
}

/** Moves the highlight to the step Home points at, but never back: a step the user has reached stays reached. */
fun OruTourState.advanceTo(derived: OruTourStep): OruTourState =
    if (derived > step) copy(step = derived) else this

/** Shows the step the user picked, from the stage rail. Going back is allowed here. */
fun OruTourState.goTo(picked: OruTourStep): OruTourState = copy(step = picked)

/** The Next action: one step forward, held at the last step. */
fun OruTourState.next(): OruTourState = copy(step = step.next())

/** Skip and Got it both end the tour for this session. The stored flag is written by the caller. */
fun OruTourState.dismiss(): OruTourState = copy(dismissed = true)
