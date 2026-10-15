/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.home

import androidx.compose.runtime.Immutable

/**
 * The one banner Home shows. The nine alert sources of the old Home (audit C11) are folded into
 * these kinds by [pickOruHomeBanner]. Every source stays reachable: the ones that lose the slot
 * are still raised by their own state and show once the banner above them clears.
 */
@Immutable
sealed interface OruHomeBanner {
    /** A newer Oru release is out, or its download and install is under way. Its action follows [update]. */
    data class ManagerUpdate(val update: OruManagerUpdate) : OruHomeBanner

    /** A source is outdated, held back, blocked or failed its metadata. Opens the sources sheet. */
    data object SourcesAttention : OruHomeBanner

    /** Updates were skipped on mobile data. Opens the settings. */
    data object Metered : OruHomeBanner

    /** A batch queue is patching. Reopens the batch screen. */
    data class BatchRunning(val processed: Int, val total: Int) : OruHomeBanner

    /** A batch queue finished and holds results. Reopens the batch screen. */
    data object BatchFinished : OruHomeBanner

    /** The patch sources are updating on their own. Nothing to tap while it runs; a failure offers a retry. */
    data class BundleUpdate(val state: OruBundleUpdateState) : OruHomeBanner

    /** Patched apps have newer patches. Queues them all. */
    data class Repatch(val count: Int) : OruHomeBanner
}

enum class OruBatchPhase { Idle, Running, Finished }

/**
 * Where the Oru self-update stands on the banner. A checking state is not shown, since a check runs on
 * every start and a banner for it would only flicker.
 */
sealed interface OruManagerUpdate {
    /** A release newer than this build is out and can be downloaded. The action downloads and installs it. */
    data class Available(val version: String) : OruManagerUpdate

    /** The APK is downloading. [percent] is null while the size is not known. */
    data class Downloading(val percent: Int?) : OruManagerUpdate

    /** The APK is downloaded and checked, and waits for the install. */
    data object Ready : OruManagerUpdate

    /** The installer has the APK. */
    data object Installing : OruManagerUpdate

    /** The last attempt failed. [message] says why, and the action tries again. */
    data class Failed(val message: String) : OruManagerUpdate
}

/** Where the automatic patch source update is: running, finished well, or failed (the saved sources stay in use). */
enum class OruBundleUpdateState { Updating, Done, Failed }

/** The batch queue as the banner sees it. [processed] and [total] only matter while running. */
data class OruBatchSignal(
    val phase: OruBatchPhase,
    val processed: Int = 0,
    val total: Int = 0,
)

/** The facts the banner picker reads. Each flag is one of the nine sources, grouped by what it asks of the user. */
data class OruBannerInput(
    /** Null when there is no manager update to show. */
    val managerUpdate: OruManagerUpdate?,
    /** Outdated manager, held back, blocked or metadata errors, in any combination. */
    val sourcesAttention: Boolean,
    val meteredSkipped: Boolean,
    val batch: OruBatchSignal,
    /** Null when no update is running or showing its result. */
    val bundleUpdate: OruBundleUpdateState?,
)

/**
 * The banner to show, in priority order: manager update, sources, metered, batch running, batch
 * finished, bundle update, repatch. [repatchCount] is the number of patched apps whose badge says
 * newer patches are out, and zero hides that banner. Null when nothing needs the user.
 */
fun pickOruHomeBanner(input: OruBannerInput, repatchCount: Int): OruHomeBanner? {
    val manager = input.managerUpdate
    return when {
        manager != null -> OruHomeBanner.ManagerUpdate(manager)
        input.sourcesAttention -> OruHomeBanner.SourcesAttention
        input.meteredSkipped -> OruHomeBanner.Metered
        input.batch.phase == OruBatchPhase.Running ->
            OruHomeBanner.BatchRunning(input.batch.processed, input.batch.total)
        input.batch.phase == OruBatchPhase.Finished -> OruHomeBanner.BatchFinished
        input.bundleUpdate != null -> OruHomeBanner.BundleUpdate(input.bundleUpdate)
        repatchCount > 0 -> OruHomeBanner.Repatch(repatchCount)
        else -> null
    }
}
