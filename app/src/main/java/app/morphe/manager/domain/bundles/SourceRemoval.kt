/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.bundles

/** An app that patches from a source being removed, and the source it patches from once that source is gone. */
data class AppMovedOff(val packageName: String, val to: AppSourceFact)

/** The names of some apps, as a sentence shows them: the first two, and how many others there are. */
data class AppNameList(val shown: List<String>, val others: Int)

/**
 * What removing a source does to the apps that patch from it, under the rule [AppSourceChoice.effective] applies.
 */
object SourceRemoval {

    /**
     * The apps that patch from [removedUid] now, with the source each one moves to once it is gone. [covering] lists,
     * per app, the enabled sources that cover it, and [keptFrom] the sources each app is kept from. An app that no
     * other source covers is left out: it has nothing to move to, so it only leaves the home screen.
     */
    fun appsMovedOff(
        removedUid: Int,
        covering: Map<String, List<AppSourceFact>>,
        keptFrom: Map<String, Set<Int>>,
    ): List<AppMovedOff> = covering.mapNotNull { (packageName, facts) ->
        val turnedDown = keptFrom[packageName].orEmpty()
        if (AppSourceChoice.effective(facts, turnedDown)?.uid != removedUid) return@mapNotNull null
        val remaining = facts.filterNot { it.uid == removedUid }
        AppSourceChoice.effective(remaining, turnedDown)?.let { AppMovedOff(packageName, it) }
    }.sortedBy { it.packageName }

    /** The source every app in [moves] moves to, or null when they do not all move to the same one. */
    fun sharedTarget(moves: List<AppMovedOff>): AppSourceFact? {
        val targets = moves.map { it.to }.distinctBy { it.uid }
        return targets.singleOrNull()
    }

    /** The first two of [names], and how many follow them. */
    fun nameList(names: List<String>): AppNameList = AppNameList(
        shown = names.take(2),
        others = (names.size - 2).coerceAtLeast(0),
    )
}
