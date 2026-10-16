/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.bundles

import java.net.URI

/**
 * One patch source that patches an app, as the choice of source reads it. Built from the loaded sources and the
 * per-app data, so the rules in [AppSourceChoice] stay plain functions on plain values.
 */
data class AppSourceFact(
    val uid: Int,
    /** The name the source is shown under, before two shared names are told apart. */
    val name: String,
    /** The pre-installed source. */
    val isDefault: Boolean,
    /** owner/repo of a remote source, null for the pre-installed one. */
    val repositoryPath: String?,
    val patchVersion: String?,
    /** Patches this source has for the app. Counted per source, never summed across sources. */
    val patchCount: Int,
    /** The version this source recommends for the app. */
    val recommendedVersion: String?,
)

/**
 * Which source one app is patched from. One app uses one source: the choice is stored as the sources the app is
 * turned down from (`SourceMuteRepository`), so a source the user picked turns the others down and every other
 * source stays out of it. Nothing about the choice is written by a source being added.
 */
object AppSourceChoice {

    /**
     * The source [facts] patch an app from, given the sources the app is turned down from.
     *
     * The sources not turned down are the ones the app can use. When exactly one is left, that is the source the
     * user picked. When several are left, the choice was never made, or a source was added after it was made,
     * so the pre-installed source wins if it is among them, and otherwise the one with the lowest uid. A uid is fixed
     * for a source, so the order does not depend on the name or on when a source was added, and adding a source never
     * moves an app from the source it uses. When every source is turned down, the app is patched from the lowest uid
     * of them rather than left with none.
     */
    fun effective(facts: List<AppSourceFact>, turnedDown: Set<Int>): AppSourceFact? {
        val usable = facts.filterNot { it.uid in turnedDown }.ifEmpty { facts }
        return usable.singleOrNull()
            ?: usable.firstOrNull { it.isDefault }
            ?: usable.minByOrNull { it.uid }
    }

    /** Whether an app can be patched from more than one source, so that the choice is worth showing. */
    fun offersChoice(facts: List<AppSourceFact>): Boolean = facts.size > 1

    /**
     * The apps a new source must be turned down from so it does not take them over: the apps it shares with a
     * source that already patches them. An app only the new source covers is left alone, it has no other source.
     */
    fun appsToTurnDown(newSourceApps: Set<String>, otherSourcesApps: Collection<Set<String>>): Set<String> =
        newSourceApps.filterTo(mutableSetOf()) { app -> otherSourcesApps.any { app in it } }

    /**
     * The owner/repo a remote source is hosted at, for telling two sources of the same name apart. Falls back to the
     * host when the address has no path to show.
     */
    fun repositoryPathOf(endpoint: String): String? {
        val uri = runCatching { URI(endpoint) }.getOrNull() ?: return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        return segments.take(2).takeIf { it.size == 2 }?.joinToString("/") ?: uri.host
    }
}
