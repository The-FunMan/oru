/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.sources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.domain.bundles.AppSourceChoice
import app.morphe.manager.domain.bundles.AppSourceFact
import app.morphe.manager.domain.bundles.BundledAppTarget
import app.morphe.manager.domain.bundles.PatchBundleSource
import app.morphe.manager.domain.bundles.RemotePatchBundle
import app.morphe.manager.domain.bundles.recommended
import app.morphe.manager.domain.repository.PatchBundleRepository.Companion.DEFAULT_SOURCE_UID
import app.morphe.manager.domain.repository.SourceMuteRepository
import app.morphe.manager.ui.oru.focus.expert.oruSourceDisplayName
import app.morphe.manager.ui.oru.focus.expert.oruSourceLabels
import app.morphe.manager.ui.viewmodel.HomeViewModel
import org.koin.compose.koinInject

/**
 * What Oru shows of the sources one app can be patched from. [effective] is the one source the app is patched from;
 * every count, version and link of the app is read from it, never summed across [facts].
 */
@Immutable
class OruAppSources(
    /** Every source that patches the app, the turned-down ones included, lowest uid first. */
    val facts: List<AppSourceFact>,
    private val labels: Map<Int, String>,
    val effective: AppSourceFact?,
    /** The versions of the effective source only, newest first, so the version list never mixes sources. */
    val effectiveVersions: List<BundledAppTarget>,
) {
    /** Whether the app can be patched from more than one source, which is when the choice is shown. */
    val choosable: Boolean get() = AppSourceChoice.offersChoice(facts)

    /** The label of the source [uid]: its name, with its repository path where another source shares the name. */
    fun labelOf(uid: Int): String? = labels[uid]

    /** The effective source's label. */
    val effectiveLabel: String? get() = effective?.let { labels[it.uid] }

    /** The patches of the effective source for the app. */
    val patchCount: Int get() = effective?.patchCount ?: 0

    /** The version the effective source recommends for the app. */
    val recommendedVersion: String? get() = effective?.recommendedVersion

    /** The versions of the effective source, as the supported check reads them. */
    val supportedVersions: Set<String>
        get() = effectiveVersions.mapNotNullTo(mutableSetOf()) { it.target.version }

    companion object {
        val NONE = OruAppSources(emptyList(), emptyMap(), null, emptyList())
    }
}

/**
 * The [OruAppSources] of one app from its sources.
 *
 * @param facts every source that patches the app, the turned-down ones included.
 * @param versions the versions each source of the app covers, the turned-down sources included.
 * @param turnedDown the sources the app is turned down from (`SourceMuteRepository`).
 */
fun oruAppSourcesOf(
    facts: List<AppSourceFact>,
    versions: List<BundledAppTarget>,
    turnedDown: Set<Int>,
): OruAppSources {
    val sorted = facts.sortedBy { it.uid }
    val effective = AppSourceChoice.effective(sorted, turnedDown)
    return OruAppSources(
        facts = sorted,
        labels = oruSourceLabels(sorted),
        effective = effective,
        effectiveVersions = effectiveVersionsOf(versions, effective?.uid),
    )
}

/**
 * The source chip of each app: the name of the source the app is patched from, for an app more than one source can
 * patch. An app with a single source gets no chip.
 */
fun oruSourceChips(apps: Map<String, OruAppSources>): Map<String, String> =
    apps.mapNotNull { (packageName, sources) ->
        if (sources.choosable) sources.effectiveLabel?.let { packageName to it } else null
    }.toMap()

/** The versions of the source [uid] out of [versions], newest first. None when there is no source. */
fun effectiveVersionsOf(versions: List<BundledAppTarget>, uid: Int?): List<BundledAppTarget> {
    if (uid == null) return emptyList()
    return versions.filter { it.bundleUid == uid }.sortedByDescending { it.target }
}

/**
 * Builds the [OruAppSources] of one app from the loaded sources.
 *
 * @param patchCounts patches per source uid for the app, for the enabled sources that have any.
 * @param sources every loaded source by uid, to read the name, the version and the address of each.
 */
fun oruAppSources(
    patchCounts: Map<Int, Int>,
    sources: Map<Int, PatchBundleSource>,
    versions: List<BundledAppTarget>,
    turnedDown: Set<Int>,
): OruAppSources {
    val facts = patchCounts.mapNotNull { (uid, count) ->
        val source = sources[uid] ?: return@mapNotNull null
        AppSourceFact(
            uid = uid,
            name = oruSourceDisplayName(source),
            isDefault = uid == DEFAULT_SOURCE_UID,
            repositoryPath = (source as? RemotePatchBundle)?.endpoint?.let(AppSourceChoice::repositoryPathOf),
            patchVersion = source.version,
            patchCount = count,
            recommendedVersion = versions.filter { it.bundleUid == uid }.recommended()?.version,
        )
    }
    return oruAppSourcesOf(facts, versions, turnedDown)
}

/** The sources of [packageName] as the Focus and the Home card read them, kept up to date as the data changes. */
@Composable
fun rememberOruAppSources(home: HomeViewModel, packageName: String): OruAppSources {
    val sourceMute: SourceMuteRepository = koinInject()
    val loaded by home.patchBundleRepository.sources.collectAsStateWithLifecycle()
    val versions by home.sourceVersionsFlow.collectAsStateWithLifecycle()
    val turnedDown by sourceMute.mutedFor(packageName).collectAsStateWithLifecycle(initialValue = emptySet())
    return remember(packageName, loaded, versions, turnedDown) {
        oruAppSources(
            patchCounts = home.getPatchesForPackage(packageName).mapValues { it.value.size },
            sources = loaded.associateBy { it.uid },
            versions = versions[packageName].orEmpty(),
            turnedDown = turnedDown,
        )
    }
}

/** The sources of [packageName] as they are now, for the actions that start a run from them. */
suspend fun oruAppSourcesNow(home: HomeViewModel, packageName: String): OruAppSources = oruAppSources(
    patchCounts = home.getPatchesForPackage(packageName).mapValues { it.value.size },
    sources = home.patchBundleRepository.sources.value.associateBy { it.uid },
    versions = home.sourceVersionsFlow.value[packageName].orEmpty(),
    turnedDown = home.turnedDownSourcesFor(packageName),
)
