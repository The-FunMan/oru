/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.oru.sources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.domain.bundles.AppSourceFact
import app.morphe.manager.domain.bundles.PatchBundleSource
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.usesPrerelease
import app.morphe.manager.domain.repository.PatchOptionsRepository
import app.morphe.manager.domain.repository.PatchSelectionRepository
import app.morphe.manager.domain.repository.SourceMuteRepository
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.patcher.patch.PatchAvailability
import org.koin.compose.koinInject

/**
 * What one app's Expert choices change from the recommended setup. Simple reads [isCustomized] for its badge and
 * [resetPlan] for its reset. The pre-release switch belongs to the source, not to the app: it counts as a change,
 * but the reset never clears it.
 */
data class OruAppOverrides(
    /** The app is patched from a source other than the pre-installed one. */
    val otherSource: Boolean,
    /** The source the app is patched from has its pre-release channel on. */
    val preRelease: Boolean,
    /** A source's saved patch selection differs from the one that source recommends, among the patches that apply. */
    val patchesChanged: Boolean,
    /** A patch option of the app is set. */
    val optionsSet: Boolean,
    /** The app is turned down from at least one source. */
    val turnedDown: Boolean,
) {
    /** Whether Expert changed anything about the app. */
    fun isCustomized(): Boolean = otherSource || preRelease || patchesChanged || optionsSet

    /** What resetting the app clears: its source turn-downs, its saved patch selection and its saved options. */
    fun resetPlan(): OruResetPlan = OruResetPlan(
        sources = turnedDown,
        patches = patchesChanged,
        options = optionsSet,
    )
}

/** The per-app records [resetOruAppOverrides] clears. Each part is cleared only when it holds an override. */
data class OruResetPlan(val sources: Boolean, val patches: Boolean, val options: Boolean)

/**
 * The overrides of one app from the values read off its records. [effective] is the source the app is patched from,
 * [turnedDown] the sources it is turned down from, [selection] the saved patch selection per source, [recommended]
 * the selection each source recommends, [applicable] the patches each source has that the current installer and
 * architecture do not drop, and [optionBundles] the sources that have a saved option.
 */
fun oruAppOverrides(
    effective: AppSourceFact?,
    preRelease: Boolean,
    turnedDown: Set<Int>,
    selection: Map<Int, Set<String>>,
    recommended: Map<Int, Set<String>>,
    applicable: Map<Int, Set<String>>,
    optionBundles: Set<Int>,
): OruAppOverrides = OruAppOverrides(
    otherSource = effective != null && !effective.isDefault,
    preRelease = effective != null && preRelease,
    // Only the saved names that still exist and still apply count. A patch that was dropped, renamed or no longer
    // offered for this installer is not a choice the user made, so it never marks the app as changed.
    patchesChanged = selection.any { (uid, names) ->
        names.intersect(applicable[uid].orEmpty()) != recommended[uid].orEmpty()
    },
    optionsSet = optionBundles.isNotEmpty(),
    turnedDown = turnedDown.isNotEmpty(),
)

/**
 * Reads the overrides of [packageName] from the repositories. [effective] is the source the app is patched from, and
 * [loaded] the loaded sources by uid, which say whether that source's pre-release channel is on.
 */
suspend fun loadOruAppOverrides(
    home: HomeViewModel,
    sourceMute: SourceMuteRepository,
    selections: PatchSelectionRepository,
    options: PatchOptionsRepository,
    packageName: String,
    effective: AppSourceFact?,
    loaded: Map<Int, PatchBundleSource>,
): OruAppOverrides {
    val patches = home.getPatchesForPackage(packageName)
    val installer = home.currentInstallerType
    val architecture = home.currentApkArchitecture
    // The selection a source starts from when nothing is saved: the rule the patch selection itself uses
    val recommended = patches.mapValues { (_, list) ->
        list.filter { it.defaultSelected(installer, architecture) }.mapTo(mutableSetOf()) { it.name }
    }
    // The patches the current installer and architecture keep. An unavailable one is dropped from any selection
    val applicable = patches.mapValues { (_, list) ->
        list.filter { it.availabilityResolver?.resolve(installer, architecture) != PatchAvailability.UNAVAILABLE }
            .mapTo(mutableSetOf()) { it.name }
    }
    val savedOptions = options.getAllOptionsForPackage(
        packageName,
        patches.mapValues { (_, list) -> list.associateBy { it.name } },
    )
    return oruAppOverrides(
        effective = effective,
        preRelease = effective?.let { loaded[it.uid]?.usesPrerelease } == true,
        turnedDown = sourceMute.getMutedFor(packageName),
        selection = selections.getAllSelectionsForPackage(packageName),
        recommended = recommended,
        applicable = applicable,
        optionBundles = savedOptions.filterValues { it.isNotEmpty() }.keys,
    )
}

/** Clears the records of [plan] for [packageName]. The only writer of a reset. */
suspend fun resetOruAppOverrides(
    sourceMute: SourceMuteRepository,
    selections: PatchSelectionRepository,
    options: PatchOptionsRepository,
    packageName: String,
    plan: OruResetPlan,
) {
    if (plan.sources) sourceMute.unmuteAll(packageName)
    if (plan.patches) selections.resetSelectionForPackage(packageName)
    if (plan.options) options.resetOptionsForPackage(packageName)
}

/** The overrides of one app, read again when its records or its sources change. Null until the first read. */
@Composable
fun rememberOruAppOverrides(home: HomeViewModel, packageName: String, sources: OruAppSources): OruAppOverrides? {
    val sourceMute: SourceMuteRepository = koinInject()
    val selections: PatchSelectionRepository = koinInject()
    val options: PatchOptionsRepository = koinInject()
    val loaded by home.patchBundleRepository.sources.collectAsStateWithLifecycle()
    val selectionSummary by remember(selections) { selections.getSelectionsSummaryFlow() }.collectAsStateWithLifecycle(emptyMap())
    val optionPackages by remember(options) { options.getPackagesWithSavedOptions() }.collectAsStateWithLifecycle(emptySet())
    val turnedDown by remember(sourceMute, packageName) { sourceMute.mutedFor(packageName) }.collectAsStateWithLifecycle(emptySet())
    val effective = sources.effective
    return produceState<OruAppOverrides?>(
        initialValue = null,
        packageName,
        effective,
        loaded,
        selectionSummary,
        optionPackages,
        turnedDown,
    ) {
        value = loadOruAppOverrides(
            home = home,
            sourceMute = sourceMute,
            selections = selections,
            options = options,
            packageName = packageName,
            effective = effective,
            loaded = loaded.associateBy { it.uid },
        )
    }.value
}

/** The packages of the Home list whose overrides [isCustomized]. Read again as the list or its records change. */
@Composable
fun rememberOruCustomizedApps(
    home: HomeViewModel,
    packages: List<String>,
    appSources: Map<String, OruAppSources>,
): Set<String> {
    val sourceMute: SourceMuteRepository = koinInject()
    val selections: PatchSelectionRepository = koinInject()
    val options: PatchOptionsRepository = koinInject()
    val loaded by home.patchBundleRepository.sources.collectAsStateWithLifecycle()
    val selectionSummary by remember(selections) { selections.getSelectionsSummaryFlow() }.collectAsStateWithLifecycle(emptyMap())
    val optionPackages by remember(options) { options.getPackagesWithSavedOptions() }.collectAsStateWithLifecycle(emptySet())
    val turnedDown by sourceMute.mutedSources.collectAsStateWithLifecycle(emptyMap())
    return produceState(
        initialValue = emptySet<String>(),
        packages,
        appSources,
        loaded,
        selectionSummary,
        optionPackages,
        turnedDown,
    ) {
        val byUid = loaded.associateBy { it.uid }
        value = packages.filterTo(mutableSetOf()) { packageName ->
            loadOruAppOverrides(
                home = home,
                sourceMute = sourceMute,
                selections = selections,
                options = options,
                packageName = packageName,
                effective = appSources[packageName]?.effective,
                loaded = byUid,
            ).isCustomized()
        }
    }.value
}
