/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus

import android.app.Application
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import app.morphe.manager.domain.batch.BatchPatchCoordinator
import app.morphe.manager.domain.batch.BatchPhase
import app.morphe.manager.ui.oru.install.OruInstallCoordinator
import app.morphe.manager.ui.oru.install.OruInstallUi
import app.morphe.manager.ui.oru.install.OruInstallerOption
import app.morphe.manager.ui.oru.run.oruRunFill
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.model.navigation.Patcher
import app.morphe.manager.ui.viewmodel.PatcherViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf
import java.io.File

/**
 * Seam interface for the patcher run owned by [SingleRunOwner].
 * Allows unit tests to inject a fake patcher without pulling in the entire ViewModel dependency graph.
 */
interface SingleRunPatcher {
    val progress: Float
    val patchesProgress: Pair<Int, Int>
    val patcherSucceeded: StateFlow<Boolean?>
    val patcherCancelled: StateFlow<Boolean>
    val showSuccessScreen: Boolean
    fun cancelPatching()
    fun destroy()
}

/**
 * An active run owned at application scope. A plain class: a run is the object the owner holds, so two runs of
 * the same app are told apart by identity, not by their fields.
 */
class SingleRun(
    val packageKey: String,
    val item: HomeAppItem,
    val params: Patcher.ViewModelParams,
    val patcher: SingleRunPatcher,
)

/** Where the install of a finished run stands. */
internal enum class InstallPhase {
    /** No install has been asked for. */
    NONE,

    /** The install is asked for, and its coordinator is not built yet. */
    PENDING,

    /** The coordinator runs and has no outcome yet. */
    RUNNING,

    /** The install reached an outcome, installed or failed. */
    SETTLED,
}

/** Whether an install screen state is an outcome: installed or failed. */
internal fun isInstallSettled(ui: OruInstallUi): Boolean =
    ui is OruInstallUi.Installed || ui is OruInstallUi.Failed

/**
 * Whether the single run holds the one-run rule. It does while it works, and while a finished run's install has
 * not reached an outcome. A failed or cancelled run does not, and neither does a finished run whose install was
 * never begun, so the next start releases it.
 */
internal fun isRunBusy(hasRun: Boolean, status: OruRunStatus?, install: InstallPhase): Boolean {
    if (!hasRun) return false
    return when (status) {
        null, OruRunStatus.Working -> true
        OruRunStatus.Done -> install == InstallPhase.PENDING || install == InstallPhase.RUNNING
        OruRunStatus.Failed, OruRunStatus.Cancelled -> false
    }
}

/**
 * Application-scoped owner of the single-app patch run.
 *
 * Ensures that leaving the Focus screen or backgrounding the app does not cancel the WorkManager worker or lose
 * progress. Manages the lifecycle of [PatcherViewModel] outside any ViewModelStore so it is not destroyed by
 * navigation pops. The install of a finished run is owned here too, so it is not cancelled with the Focus.
 *
 * Tracks live progress and publishes to [ActiveRunTracker] for the Home island accessory.
 * Enforces one run at a time (see [isBusy]) and detects ungraceful process death.
 */
class SingleRunOwner(
    private val app: Application? = null,
    private val runs: ActiveRunTracker,
    private val filesDir: File = app?.filesDir ?: File(System.getProperty("java.io.tmpdir") ?: "."),
    private val patcherFactory: (Patcher.ViewModelParams) -> SingleRunPatcher = { params ->
        GlobalContext.get().get<PatcherViewModel> {
            parametersOf(params, SavedStateHandle())
        }
    },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : KoinComponent {

    private val _currentRun = MutableStateFlow<SingleRun?>(null)
    val currentRun: StateFlow<SingleRun?> = _currentRun.asStateFlow()

    private val _runStatus = MutableStateFlow<OruRunStatus?>(null)
    val runStatus: StateFlow<OruRunStatus?> = _runStatus.asStateFlow()

    private val _install = MutableStateFlow<OruInstallCoordinator?>(null)

    /** The install of the finished run, once it is begun. Null before that and after the run is released. */
    val install: StateFlow<OruInstallCoordinator?> = _install.asStateFlow()

    private val _rootMode = MutableStateFlow<Boolean?>(null)

    /** The root patch mode chosen in the install (true for Root mount), null until one is chosen. */
    val rootMode: StateFlow<Boolean?> = _rootMode.asStateFlow()

    private val _stoppedPackageKey = MutableStateFlow<String?>(null)

    /** The Focus key of the run that the process died in, until that Focus starts again. */
    val stoppedPackageKey: StateFlow<String?> = _stoppedPackageKey.asStateFlow()

    private var trackingJob: Job? = null
    private var installJob: Job? = null
    private var installRequested = false

    var lastRunWasStopped: Boolean = false
        private set

    init {
        checkProcessDeath()
    }

    /** Whether a run is working, or waiting to start. The run accessory and the Focus read this. */
    val isActive: Boolean
        get() = _currentRun.value != null && (_runStatus.value == null || _runStatus.value == OruRunStatus.Working)

    /** Whether the one-run rule holds: a run is working, or a finished run's install has no outcome yet. */
    val isBusy: Boolean
        get() = isRunBusy(
            hasRun = _currentRun.value != null,
            status = _runStatus.value,
            install = installPhase(),
        )

    val activePackageKey: String?
        get() = _currentRun.value?.packageKey

    private fun installPhase(): InstallPhase {
        val coordinator = _install.value
        return when {
            coordinator == null -> if (installRequested) InstallPhase.PENDING else InstallPhase.NONE
            isInstallSettled(coordinator.state.value.ui) -> InstallPhase.SETTLED
            else -> InstallPhase.RUNNING
        }
    }

    private fun checkProcessDeath() {
        val stopFlag = File(filesDir, STOP_FLAG_FILE)
        if (stopFlag.exists()) {
            // The flag holds the Focus key of the run that was working when the process died
            val key = runCatching { stopFlag.readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
            stopFlag.delete()
            lastRunWasStopped = true
            _stoppedPackageKey.value = key
        }
    }

    fun consumeLastRunStopped(): Boolean {
        val stopped = lastRunWasStopped
        lastRunWasStopped = false
        return stopped
    }

    /** Forgets the stopped run of [packageKey] once its Focus starts again. */
    fun clearStoppedRun(packageKey: String) {
        if (_stoppedPackageKey.value == packageKey) _stoppedPackageKey.value = null
    }

    private fun markRunStarted(packageKey: String) {
        runCatching {
            File(filesDir, STOP_FLAG_FILE).writeText(packageKey)
        }
    }

    private fun markRunFinished() {
        runCatching {
            File(filesDir, STOP_FLAG_FILE).delete()
        }
    }

    /**
     * Whether a new run may start: nothing is busy (see [isBusy]), and no batch queue is patching.
     */
    fun canStart(): Boolean = !isBusy && !batchRunning()

    private fun batchRunning(): Boolean = runCatching {
        GlobalContext.get().getOrNull<BatchPatchCoordinator>()?.state?.value?.phase == BatchPhase.RUNNING
    }.getOrDefault(false)

    /**
     * Returns the patcher of the run for [packageKey], starting one when there is none to attach to.
     *
     * A busy run of the same Focus is returned as it is. A run that is not busy (failed, cancelled, or finished
     * and installed) is released first, so every start gets a patcher of its own. Returns null while another
     * run is busy or a batch queue is patching.
     */
    fun getOrStartRun(
        packageKey: String,
        item: HomeAppItem,
        params: Patcher.ViewModelParams,
    ): SingleRunPatcher? {
        clearStoppedRun(packageKey)
        if (_currentRun.value != null) {
            if (isBusy) return _currentRun.value?.takeIf { it.packageKey == packageKey }?.patcher
            finishRun()
        }
        if (!canStart()) return null

        markRunStarted(packageKey)
        val focused = params.copy(focusKey = packageKey)
        val patcher = patcherFactory(focused)
        val run = SingleRun(
            packageKey = packageKey,
            item = item,
            params = focused,
            patcher = patcher,
        )
        _currentRun.value = run
        _runStatus.value = OruRunStatus.Working

        runs.claim(this)
        startTracking(run)
        return patcher
    }

    /**
     * Returns the patcher of the current run if it belongs to [packageKey].
     */
    fun getRun(packageKey: String): SingleRunPatcher? {
        val current = _currentRun.value ?: return null
        return if (current.packageKey == packageKey) current.patcher else null
    }

    private fun startTracking(run: SingleRun) {
        trackingJob?.cancel()
        trackingJob = scope.launch {
            // Follow progress for the Home accessory while the run works
            launch {
                snapshotFlow { run.patcher.progress to run.patcher.patchesProgress }.collect { (progress, patches) ->
                    if (_currentRun.value !== run || _runStatus.value != OruRunStatus.Working) return@collect
                    runs.publish(
                        owner = this@SingleRunOwner,
                        run = ActiveRun(
                            packageKey = run.packageKey,
                            packageName = run.item.packageName,
                            label = run.item.displayName,
                            // The same fill as the run screen: a single run goes on to the install, see OruFocusRun
                            progress = oruRunFill(progress, installAhead = true),
                            done = patches.first,
                            total = patches.second,
                        )
                    )
                }
            }

            // Follow run outcome
            combine(
                run.patcher.patcherSucceeded,
                run.patcher.patcherCancelled,
                snapshotFlow { run.patcher.showSuccessScreen },
            ) { succeeded, cancelled, resultShown ->
                when {
                    cancelled -> OruRunStatus.Cancelled
                    succeeded == true && resultShown -> OruRunStatus.Done
                    succeeded == false -> OruRunStatus.Failed
                    else -> OruRunStatus.Working
                }
            }.collect { status ->
                if (_currentRun.value !== run) return@collect
                _runStatus.value = status
                if (status != OruRunStatus.Working) {
                    runs.clear(this@SingleRunOwner)
                    markRunFinished()
                }
            }
        }
    }

    /**
     * Begins the install of the current run, once. The Focus calls this when the run reports Done. [make] builds the
     * coordinator; it runs in the owner's scope, so leaving the Focus does not cancel the install. [picked] is the
     * installer the user chose before the install. Returns false when there is no run, or an install is already begun.
     *
     * The run counts as busy from here on (see [isBusy]), even before the owner's own status has caught up with Done.
     */
    fun beginInstall(
        picked: OruInstallerOption?,
        make: suspend () -> OruInstallCoordinator,
    ): Boolean {
        val run = _currentRun.value ?: return false
        if (installRequested) return false
        installRequested = true
        installJob = scope.launch {
            val coordinator = make()
            if (_currentRun.value !== run) return@launch
            _install.value = coordinator
            // The run is released once the install has an outcome, so the Focus need not be open for that
            launch {
                coordinator.state.first { isInstallSettled(it.ui) }
                if (_currentRun.value === run) finishRun()
            }
            picked?.let { coordinator.pickInstaller(it) }
            coordinator.start()
        }
        return true
    }

    /** Records the root patch mode the user chose in the install of the run. */
    fun recordRootMode(useMount: Boolean) {
        _rootMode.value = useMount
    }

    /** Releases the run only when nothing is busy, so a start of another run never replaces a busy one. */
    fun finishIdleRun() {
        if (_currentRun.value != null && !isBusy) finishRun()
    }

    /** Explicitly cancels the active run. */
    fun cancelRun() {
        val run = _currentRun.value ?: return
        run.patcher.cancelPatching()
        _runStatus.value = OruRunStatus.Cancelled
        runs.clear(this)
        markRunFinished()
    }

    /** Releases the run: stops its tracking and install, clears the accessory and the stop flag, and destroys the patcher. */
    fun finishRun() {
        trackingJob?.cancel()
        trackingJob = null
        installJob?.cancel()
        installJob = null
        installRequested = false
        val run = _currentRun.value
        _currentRun.value = null
        _runStatus.value = null
        _install.value = null
        _rootMode.value = null
        runs.clear(this)
        markRunFinished()
        run?.patcher?.destroy()
    }

    companion object {
        private const val STOP_FLAG_FILE = "oru_active_run_flag"
    }
}
