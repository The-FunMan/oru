/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.manager

import app.morphe.manager.BuildConfig

/**
 * Behaviour that differs between the morphe and oru flavours.
 *
 * [upstreamServices] is true in the morphe flavour, which talks to Morphe's own services, and false
 * in oru. Oru talks to no Morphe-run host at all: the APKMirror link is built on the device
 * ([ApkMirrorLinks]) and the blocked-sources list is not fetched.
 * The manager self-update is not switched here: each flavour checks its own feed, see ManagerUpdateRepository.
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
     * Alias and key password for a signing keystore created from now on. Null in morphe, which keeps
     * its stored values. Keystores that already exist keep the alias stored with them.
     */
    val newKeystoreAlias: String? = if (upstreamServices) null else ORU_KEYSTORE_ALIAS

    companion object {
        private const val ORU_KEYSTORE_ALIAS = "oru"

        /** The endpoints of the flavour this build was made from. */
        val current = OruEndpoints(upstreamServices = BuildConfig.UPSTREAM_SERVICES)
    }
}
