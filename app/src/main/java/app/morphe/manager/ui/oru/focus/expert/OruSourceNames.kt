/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus.expert

import app.morphe.manager.domain.bundles.AppSourceFact
import app.morphe.manager.domain.bundles.PatchBundleSource
import app.morphe.manager.domain.repository.PatchBundleRepository.Companion.DEFAULT_SOURCE_UID

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
 * The label each source of an app is shown under: its name, with its repository path after it where another source
 * of the app has the same name. A pre-installed source has no path, so a shared name stays as it is for it.
 */
fun oruSourceLabels(facts: List<AppSourceFact>): Map<Int, String> {
    val sharing = facts.groupingBy { it.name.trim().lowercase() }.eachCount()
    return facts.associate { fact ->
        val path = fact.repositoryPath
        val shared = sharing[fact.name.trim().lowercase()]?.let { it > 1 } == true
        fact.uid to if (shared && path != null) "${fact.name} ($path)" else fact.name
    }
}
