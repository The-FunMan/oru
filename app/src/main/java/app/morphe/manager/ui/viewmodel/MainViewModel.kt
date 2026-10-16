/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.BuildConfig
import app.morphe.manager.domain.batch.BatchPatchCoordinator
import app.morphe.manager.domain.batch.BatchPlanResolver
import app.morphe.manager.domain.batch.BatchTarget
import app.morphe.manager.domain.manager.PreferencesManager
import kotlinx.coroutines.launch

class MainViewModel(
    val prefs: PreferencesManager,
    private val batchPlanResolver: BatchPlanResolver,
    private val batchPatchCoordinator: BatchPatchCoordinator
) : ViewModel() {

    /**
     * Set by [app.morphe.manager.MainActivity.onNewIntent] when the user taps an FCM
     * update notification. HomeScreen observes this via LaunchedEffect, triggers
     * an update check, then resets the flag back to false.
     */
    var pendingUpdateCheck by mutableStateOf(false)

    /**
     * Set by [app.morphe.manager.MainActivity.handleDeepLinkIntent] when the changelog action of
     * an update notification is tapped. MorpheManager shows it, then resets the flag to null.
     */
    var pendingBundleChangelogUid: Int? by mutableStateOf(null)

    /**
     * Set by [app.morphe.manager.MainActivity.handleDeepLinkIntent] when the changelog action of
     * a manager update notification is tapped. MorpheManager shows it, then clears the flag.
     */
    var pendingManagerChangelog by mutableStateOf(false)

    /**
     * Set by [app.morphe.manager.MainActivity.handleDeepLinkIntent] when the app is opened
     * via a deep link to add a patch source. HomeScreen observes this via LaunchedEffect,
     * shows a confirmation dialog, then resets the flag to null.
     */
    var pendingDeepLinkSource: DeepLinkSource? by mutableStateOf(null)

    /**
     * Set by [app.morphe.manager.MainActivity.handleDeepLinkIntent] when the app is opened
     * by tapping a .mpp file in a file manager. HomeScreen observes this via LaunchedEffect,
     * shows a confirmation dialog, then resets the flag to null.
     */
    var pendingMppUri: Uri? by mutableStateOf(null)

    /**
     * Set by [app.morphe.manager.MainActivity.handleDeepLinkIntent] when an APK-family file
     * is shared to Morphe via the system share sheet.
     * HomeScreen observes this via LaunchedEffect, triggers
     * [app.morphe.manager.ui.viewmodel.HomeViewModel.handleExternalApkUri], then resets to null.
     */
    var pendingExternalApkUri: Uri? by mutableStateOf(null)

    /** Production rules for the external trigger. */
    private val externalBatchGate: ExternalBatchGate = FlavorExternalBatchGate(oruUx = true)

    /** Batch patch request received from another app, waiting to be gated. */
    var pendingBatchPatch: BatchPatchRequest? by mutableStateOf(null)

    /** Set by the launcher shortcut, which asks the app to work out what needs re-patching. */
    var pendingOutdatedBatch by mutableStateOf(false)

    /** Package a per-app launcher shortcut wants the patch dialog opened for. */
    var pendingPatchPackage: String? by mutableStateOf(null)

    /** ORU: Package that a completed run or deep link notification wants to return Focus to. */
    var pendingFocusPackage: String? by mutableStateOf(null)

    /** Set when an automatic re-patch notification asks to reopen its queue. */
    var pendingBatchResult by mutableStateOf(false)

    /** True once a shortcut run found nothing to do, so the UI can say so and move on. */
    var nothingToRepatch by mutableStateOf(false)
        private set

    /** Request currently shown in the confirmation dialog. */
    var batchPatchConfirmation: BatchPatchRequest? by mutableStateOf(null)
        private set

    /** Request the user let through, consumed by the navigation host. */
    var approvedBatchPatch: BatchPatchRequest? by mutableStateOf(null)
        private set

    /**
     * Decides what happens to an incoming batch request. Starting a queue installs software,
     * so an unknown caller never gets through without the user saying so, and the whole
     * entry point stays off until it is enabled in settings.
     */
    fun onExternalBatchRequest(request: BatchPatchRequest) {
        pendingBatchPatch = null
        viewModelScope.launch {
            // ORU: the verdict rules live in ExternalBatchGate so they can be unit tested
            val verdict = externalBatchGate.verdict(
                switchOn = prefs.externalBatchPatchEnabled.get(),
                callerPackage = request.callerPackage,
                allowlist = prefs.externalBatchPatchAllowlist.get()
            )
            when (verdict) {
                ExternalBatchVerdict.IGNORE -> Unit
                ExternalBatchVerdict.APPROVE -> approvedBatchPatch = request
                ExternalBatchVerdict.CONFIRM -> batchPatchConfirmation = request
            }
        }
    }

    /**
     * @param trustCaller Remembers the calling package so later requests from it skip the dialog.
     */
    fun approveExternalBatch(trustCaller: Boolean) {
        val request = batchPatchConfirmation ?: return
        batchPatchConfirmation = null
        viewModelScope.launch {
            val caller = request.callerPackage
            // ORU: oru never stores a trusted caller, so the allowlist only ever shrinks there
            if (trustCaller && caller != null && externalBatchGate.mayTrustCaller()) {
                prefs.externalBatchPatchAllowlist.update(
                    prefs.externalBatchPatchAllowlist.get() + caller
                )
            }
            approvedBatchPatch = request
        }
    }

    /**
     * Resolves the apps the launcher shortcut or the re-patch notification asked about. No
     * confirmation dialog here: the request comes from Morphe itself and only opens the
     * preflight list, which the user still has to start by hand.
     */
    fun onShortcutBatchRequest() {
        pendingOutdatedBatch = false
        viewModelScope.launch {
            val targets = batchPlanResolver.findOutdatedTargets()
            if (targets.isEmpty()) {
                nothingToRepatch = true
            } else {
                approvedBatchPatch = BatchPatchRequest(targets, callerPackage = null)
            }
        }
    }

    fun consumeNothingToRepatch() {
        nothingToRepatch = false
    }

    /**
     * Reopens the queue a run finished in the background. Navigating with its own target list
     * keeps the finished state, which the batch screen would otherwise re-plan away.
     */
    fun onShowBatchResult() {
        pendingBatchResult = false
        val targets = batchPatchCoordinator.state.value?.targets
        if (targets.isNullOrEmpty()) return
        approvedBatchPatch = BatchPatchRequest(targets, callerPackage = null)
    }

    fun dismissExternalBatch() {
        batchPatchConfirmation = null
    }

    fun consumeApprovedBatch() {
        approvedBatchPatch = null
    }

    data class DeepLinkSource(val url: String, val name: String?)

    /**
     * A batch patch run requested through [app.morphe.manager.MainActivity.ACTION_BATCH_PATCH].
     *
     * @param callerPackage Package that sent the intent, null when it could not be determined.
     */
    data class BatchPatchRequest(
        val targets: List<BatchTarget>,
        val callerPackage: String?
    )
}

/** What happens to one external batch request. */
enum class ExternalBatchVerdict { IGNORE, CONFIRM, APPROVE }

/**
 * ORU: rules of the external batch trigger, free of Android types so they can be unit tested.
 * [MainViewModel] reads the preferences and asks this gate what to do with each request.
 */
interface ExternalBatchGate {
    /** Decides a request. The switch off ignores everything; trusted callers skip the dialog only where allowed. */
    fun verdict(switchOn: Boolean, callerPackage: String?, allowlist: Set<String>): ExternalBatchVerdict

    /** Whether "always allow" may store a caller in the allowlist. */
    fun mayTrustCaller(): Boolean
}

/**
 * The gate used in the app is built with [oruUx] = true. Tests build it both ways.
 */
class FlavorExternalBatchGate(private val oruUx: Boolean) : ExternalBatchGate {
    override fun verdict(
        switchOn: Boolean,
        callerPackage: String?,
        allowlist: Set<String>
    ): ExternalBatchVerdict = when {
        !switchOn -> ExternalBatchVerdict.IGNORE
        !oruUx && callerPackage != null && callerPackage in allowlist -> ExternalBatchVerdict.APPROVE
        else -> ExternalBatchVerdict.CONFIRM
    }

    override fun mayTrustCaller(): Boolean = !oruUx
}
