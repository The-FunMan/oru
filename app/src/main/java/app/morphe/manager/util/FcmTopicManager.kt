/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

/**
 * FCM topic strings used for update push notifications.
 *
 * Manager and patches topics are independent — each has a stable/dev pair:
 *
 * | Topic            | Audience                                      |
 * |------------------|-----------------------------------------------|
 * | [MANAGER_STABLE] | Manager stable releases (`main` branch)       |
 * | [MANAGER_DEV]    | Manager prerelease builds (`dev` branch)      |
 * | [PATCHES_STABLE] | Patch bundle stable releases                  |
 * | [PATCHES_DEV]    | Patch bundle prerelease releases              |
 *
 * Stable topics are always subscribed when notifications are enabled —
 * a stable release (e.g. `1.5.0`) is always a valid upgrade from a dev
 * build (e.g. `1.5.0-dev.1`). Dev topics require explicit opt-in.
 */
object FcmTopics {
    const val MANAGER_STABLE = "morphe_updates"
    const val MANAGER_DEV    = "morphe_updates_dev"
    const val PATCHES_STABLE = "morphe_patches_updates"
    const val PATCHES_DEV    = "morphe_patches_updates_dev"

    val all = listOf(MANAGER_STABLE, MANAGER_DEV, PATCHES_STABLE, PATCHES_DEV)
}

/**
 * Keeps the FCM topic subscriptions in line with the user's preferences.
 *
 * Oru does not subscribe to Morphe's FCM topics, so this does nothing. Callers still pass their
 * preferences, and the parameters are kept so they do not change.
 *
 * Called from:
 * - [app.morphe.manager.ManagerApplication] on every cold start
 * - [app.morphe.manager.ui.screen.settings.advanced.UpdatesSettingsItem] on preference toggle
 */
fun syncFcmTopics(
    notificationsEnabled: Boolean,
    useManagerPrereleases: Boolean,
    usePatchesPrereleases: Boolean = false,
) {
}
