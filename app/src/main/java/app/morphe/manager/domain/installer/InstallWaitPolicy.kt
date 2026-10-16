/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.installer

/**
 * The limits of the waits an install or uninstall makes, and the decisions taken once a wait ends.
 *
 * The system reports an outcome through a status callback that can be late or never sent, so where the
 * package manager can answer the question it does: [pollUntil] reads the installed packages rather than
 * trusting the callback. Kept free of Android types so the rules run on the JVM.
 */
object InstallWaitPolicy {
    /** Log tag for every step of an install or uninstall, so a report can show where it stopped. */
    const val LOG_TAG = "OruInstall"

    /** How often the package manager is asked whether a removed app is still installed. */
    const val UNINSTALL_POLL_INTERVAL_MS = 500L

    /** How long a removal is polled for after the uninstall call. Past this the app counts as still installed. */
    const val UNINSTALL_POLL_LIMIT_MS = 20_000L

    /**
     * How long a Shizuku install waits for the session's status after the commit. Past this the package
     * manager decides (see [installLanded]).
     */
    const val INSTALL_STATUS_LIMIT_MS = 90_000L

    /**
     * Probes [probe] every [intervalMs] until it returns true or [limitMs] has passed on [now]. The probe
     * runs once more at the limit, so a change that lands right at the end still counts. Returns whether
     * the probe succeeded before the limit.
     */
    suspend fun pollUntil(
        limitMs: Long,
        intervalMs: Long,
        now: () -> Long,
        sleep: suspend (Long) -> Unit,
        probe: suspend () -> Boolean,
    ): Boolean {
        val deadline = now() + limitMs
        while (true) {
            if (probe()) return true
            val left = deadline - now()
            if (left <= 0L) return false
            sleep(minOf(intervalMs, left))
        }
    }

    /**
     * The verdict once an install's status did not arrive in time: the install landed when the installed
     * version code is the archive's. A missing package or an unreadable archive is not a landed install.
     */
    fun installLanded(installedVersionCode: Long?, archiveVersionCode: Long?): Boolean =
        installedVersionCode != null && archiveVersionCode != null && installedVersionCode == archiveVersionCode
}
