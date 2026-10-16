/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus.expert

import app.morphe.manager.domain.bundles.AppSourceChoice
import app.morphe.manager.domain.bundles.AppSourceFact
import app.morphe.manager.domain.bundles.PatchBundleSource
import app.morphe.manager.domain.bundles.RemotePatchBundle
import app.morphe.manager.domain.repository.PatchBundleRepository.Companion.DEFAULT_SOURCE_UID
import app.morphe.manager.util.shownTitle

/**
 * The name the default patch source is shown under everywhere in Oru. Its own configured name names the
 * upstream project, so it is never shown.
 */
const val ORU_DEFAULT_SOURCE_LABEL = "Default patches"

/** The name of the patch source [uid]: the neutral label for the default source, [configuredName] for the rest. */
fun oruSourceDisplayName(uid: Int, configuredName: String): String =
    if (uid == DEFAULT_SOURCE_UID) ORU_DEFAULT_SOURCE_LABEL else configuredName

/** [oruSourceDisplayName] for a loaded source, with the name it is configured under. */
fun oruSourceDisplayName(source: PatchBundleSource): String = oruSourceDisplayName(source.uid, source.displayTitle)

/**
 * The label each source of an app is shown under, as [displaySourceName] gives it.
 */
fun oruSourceLabels(facts: List<AppSourceFact>): Map<Int, String> =
    facts.associate { fact -> fact.uid to displaySourceName(fact, facts) }

/**
 * The name [source] is shown under wherever a source is named to choose it or to read it: its name, with its
 * repository path after it where another of [allSources] has the same name. Names are compared without regard to
 * case. A pre-installed source has no path, so a shared name stays as it is for it.
 */
fun displaySourceName(source: AppSourceFact, allSources: Collection<AppSourceFact>): String {
    val key = source.name.trim().lowercase()
    val shared = allSources.any { it.uid != source.uid && it.name.trim().lowercase() == key }
    val path = source.repositoryPath
    return if (shared && path != null) "${source.name} ($path)" else source.name
}

/** [source] as [displaySourceName] reads it: the name it is shown under, and the repository it is read from. */
fun PatchBundleSource.asNameFact(): AppSourceFact = AppSourceFact(
    uid = uid,
    name = shownTitle,
    isDefault = uid == DEFAULT_SOURCE_UID,
    repositoryPath = (this as? RemotePatchBundle)?.endpoint?.let(AppSourceChoice::repositoryPathOf),
    patchVersion = null,
    patchCount = 0,
    recommendedVersion = null,
)
