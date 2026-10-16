/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.manager

/**
 * Which Morphe-run services the app may use.
 *
 * [upstreamServices] is false in the app. Oru talks to no Morphe-run host at all: the APKMirror link
 * is built on the device ([ApkMirrorLinks]) and the blocked-sources list is not fetched.
 * The manager self-update is not switched here: it checks its own feed, see ManagerUpdateRepository.
 */
class OruEndpoints(private val upstreamServices: Boolean) {

    /**
     * Whether the app may call Morphe's own API host. Oru never does: the APKMirror link is built
     * on the device, the patch metadata comes from the bundle's JSON on GitHub, and there is no
     * blocked-sources fetch.
     */
    val projectApiEnabled: Boolean = upstreamServices

    /**
     * Whether owner avatars are fetched from unavatar.io. Oru never contacts that third party
     * and shows the placeholder icon instead.
     */
    val unavatarEnabled: Boolean = upstreamServices

    /**
     * Whether the background update worker should be scheduled. FCM pushes update notifications
     * on Google Play services devices only where upstream services are on, so oru runs the worker
     * there too.
     */
    fun updateWorkerScheduled(notificationsEnabled: Boolean, hasGms: Boolean): Boolean =
        notificationsEnabled && (!hasGms || !upstreamServices)

    /**
     * Alias and key password for a signing keystore created from now on. Null when upstream services
     * are on, which keeps the stored values. Keystores that already exist keep the alias stored with them.
     */
    val newKeystoreAlias: String? = if (upstreamServices) null else ORU_KEYSTORE_ALIAS

    companion object {
        private const val ORU_KEYSTORE_ALIAS = "oru"

        /** The endpoints the app uses. */
        val current = OruEndpoints(upstreamServices = false)
    }
}
