/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single-app run in progress, for the run accessory on Home. The owner publishes it while the run works. A run
 * that has an outcome is taken off, because the Focus shows the result itself, except a finished run that nothing
 * shows: that one stays as [ready] until its install is begun or the run is released.
 *
 * @param packageKey the Focus the run belongs to, which is `HomeAppItem.id` and the key of `OruFocus`.
 * @param packageName the package of that install, for the icon's tint and shape.
 * @param label the app's label as the card shows it, before `oruAppName` takes the brand word off.
 * @param progress 0 to 1 across the whole run.
 * @param done patches applied so far, out of [total].
 * @param ready the run finished and waits to be installed; the accessory says so and opens the install.
 */
data class ActiveRun(
    val packageKey: String,
    val packageName: String,
    val label: String,
    val progress: Float,
    val done: Int,
    val total: Int,
    val ready: Boolean = false,
)

/**
 * The process-scoped record of the single-app run in progress. It keeps names and numbers only, never the Focus
 * or the patcher that wrote it. The owner clears the run when the run ends or when the owner is cleared, so the
 * record does not outlive its Focus.
 */
class ActiveRunTracker {
    private val lock = Any()
    private val _run = MutableStateFlow<ActiveRun?>(null)

    /** The run in progress, or null when none is. */
    val run: StateFlow<ActiveRun?> = _run.asStateFlow()

    /** The owner that holds the record, or null. Read and written only under [lock]. */
    private var owner: Any? = null

    /** Gives the record to [owner] and clears the run shown before it. */
    fun claim(owner: Any) = synchronized(lock) {
        this.owner = owner
        _run.value = null
    }

    /**
     * Publishes [run] for [owner]. Only the owner that holds the record can publish, so an update that arrives
     * after [clear] is dropped instead of showing a run that has ended.
     */
    fun publish(owner: Any, run: ActiveRun) = synchronized(lock) {
        if (this.owner !== owner) return@synchronized
        _run.value = run
    }

    /** Clears the run and releases the record when [owner] holds it. A run of another owner is left as it is. */
    fun clear(owner: Any) = synchronized(lock) {
        if (this.owner !== owner) return@synchronized
        this.owner = null
        _run.value = null
    }
}
