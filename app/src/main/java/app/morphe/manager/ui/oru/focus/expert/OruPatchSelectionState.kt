/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus.expert

import app.morphe.manager.patcher.patch.PatchLockState
import app.morphe.manager.patcher.patch.blocksToggle
import app.morphe.manager.util.PatchSelection

/** One patch as the selection sheet reads it. Plain data, so the reducer runs without the patcher types. */
data class OruPatchItem(
    val bundleUid: Int,
    val name: String,
    val displayName: String,
    val description: String?,
    val category: String?,
    val universal: Boolean,
    val lock: PatchLockState,
    /** Selected by default for this install target (`PatchInfo.defaultSelected`). */
    val defaultSelected: Boolean,
    val optionCount: Int,
)

/**
 * Settles a selection against the availability rules of the install target, so a required patch stays on and
 * an unavailable one stays off. The sheet passes `PatchSelectionUtils.applyAvailability`.
 */
typealias OruSettle = (PatchSelection) -> PatchSelection

/**
 * The patch selection sheet as a value. Each operation returns the next state; none of them touches the view
 * model. The rules are the ones HomeViewModel uses: toggling respects the lock, select all stages universal
 * patches behind the regular ones (a second tap arms them), clear keeps the required patches, and Recommended
 * resets every source to its defaults whatever the search shows.
 *
 * [armed] holds, per source, the selection a select all last left behind. Universal patches are staged only
 * while the selection of that source is still the one armed.
 */
data class OruPatchSelectionState(
    val items: List<OruPatchItem>,
    val selected: PatchSelection,
    val query: String = "",
    val category: String? = null,
    val armed: Map<Int, Set<String>> = emptyMap(),
) {
    val selectedCount: Int get() = selected.values.sumOf { it.size }

    /** Categories the bundles declare, in the order they first appear. Empty when no patch declares one. */
    val categories: List<String> get() = items.mapNotNull { it.category }.distinct()

    /** The patches the search and the category filter let through, in the order of [items]. */
    fun visible(): List<OruPatchItem> = items.filter(::matches)

    fun withQuery(text: String): OruPatchSelectionState = copy(query = text)

    fun withCategory(name: String?): OruPatchSelectionState = copy(category = name)

    fun toggle(bundleUid: Int, name: String, settle: OruSettle): OruPatchSelectionState {
        val item = items.firstOrNull { it.bundleUid == bundleUid && it.name == name } ?: return this
        val bundle = selected[bundleUid].orEmpty()
        val on = name in bundle
        if (item.lock.blocksToggle(on)) return this
        val next = if (on) bundle - name else bundle + name
        return copy(selected = settle(selected.withSelection(bundleUid, next)))
    }

    /**
     * Selects the visible patches. Universal patches join only on a second select all, once every regular
     * visible patch is on, so a first tap never adds the ones that most often fail a run.
     */
    fun selectAll(settle: OruSettle): OruPatchSelectionState {
        val shown = visible()
        var current = selected
        for (uid in shown.map { it.bundleUid }.distinct()) {
            val patches = shown.filter { it.bundleUid == uid }
            val bundle = current[uid].orEmpty()
            val selectable = patches.filter { it.lock != PatchLockState.LOCKED_OFF }
            val regularAllOn = selectable.all { it.universal || it.name in bundle }
            val staged = if (armed[uid] == bundle && regularAllOn) {
                selectable
            } else {
                selectable.filterNot { it.universal }
            }
            current = current.withSelection(uid, bundle + staged.map { it.name })
        }
        val settled = settle(current)
        val nextArmed = armed + shown.map { it.bundleUid }.distinct().associateWith { settled[it].orEmpty() }
        return copy(selected = settled, armed = nextArmed)
    }

    /** Clears the visible patches. Required patches stay on. */
    fun clear(settle: OruSettle): OruPatchSelectionState {
        val shown = visible()
        var current = selected
        for (uid in shown.map { it.bundleUid }.distinct()) {
            val drop = shown
                .filter { it.bundleUid == uid && it.lock != PatchLockState.LOCKED_ON }
                .mapTo(mutableSetOf()) { it.name }
            current = current.withSelection(uid, current[uid].orEmpty() - drop)
        }
        return copy(selected = settle(current))
    }

    /** Resets every source to the patches the install target selects by default. Ignores the search. */
    fun recommended(settle: OruSettle): OruPatchSelectionState {
        var current = selected
        for (uid in items.map { it.bundleUid }.distinct()) {
            val defaults = items
                .filter { it.bundleUid == uid && it.defaultSelected }
                .mapTo(mutableSetOf()) { it.name }
            current = current.withSelection(uid, defaults)
        }
        return copy(selected = settle(current))
    }

    private fun matches(item: OruPatchItem): Boolean {
        val categoryOk = category == null || item.category == category
        val text = query.trim()
        val textOk = text.isEmpty() ||
                item.displayName.contains(text, ignoreCase = true) ||
                item.description?.contains(text, ignoreCase = true) == true
        return categoryOk && textOk
    }
}

/** The source [uid] holds [names], or the source is dropped when nothing is left in it. */
private fun PatchSelection.withSelection(uid: Int, names: Set<String>): PatchSelection =
    if (names.isEmpty()) this - uid else this + (uid to names)
