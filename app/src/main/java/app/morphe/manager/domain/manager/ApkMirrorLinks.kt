/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.manager

import app.morphe.manager.util.KnownApps
import java.net.URLEncoder
import java.util.Locale

/**
 * APKMirror addresses in the form the oru flavour builds on the device, so the app never asks a
 * server where a version lives. Pure: no network, no Android types.
 *
 * APKMirror files an app under a developer and an app slug, and each release under the app slug
 * with the version written with dashes: `/apk/google-inc/youtube/youtube-21-40-161-release/`.
 * Apps outside [KNOWN_APPS] get a site search for the package name and version instead.
 */
object ApkMirrorLinks {
    private const val SITE = "https://www.apkmirror.com"

    /** Where an app's pages sit on APKMirror. */
    data class Listing(val developer: String, val appSlug: String)

    /** The apps Oru knows, keyed by package name. The bundles name the same apps in [KnownApps]. */
    val KNOWN_APPS: Map<String, Listing> = mapOf(
        KnownApps.YOUTUBE to Listing(developer = "google-inc", appSlug = "youtube"),
        KnownApps.YOUTUBE_MUSIC to Listing(developer = "google-inc", appSlug = "youtube-music"),
        KnownApps.REDDIT to Listing(developer = "redditinc", appSlug = "reddit"),
    )

    /**
     * The page to open for [packageName] at [version]: the release page when the app is known and
     * the version is given, the app's own page when only the app is known, and a search otherwise.
     */
    fun downloadPage(packageName: String, version: String?): String {
        val listing = KNOWN_APPS[packageName] ?: return search(packageName, version)
        val appPage = "$SITE/apk/${listing.developer}/${listing.appSlug}/"
        val release = versionSlug(version.orEmpty()).takeIf { it.isNotEmpty() } ?: return appPage
        return "$appPage${listing.appSlug}-$release-release/"
    }

    /** A site search for [packageName], narrowed to [version] when one is given. */
    fun search(packageName: String, version: String?): String {
        val query = listOfNotNull(packageName, version?.trim()?.takeIf { it.isNotEmpty() })
            .joinToString(separator = " ")
        return "$SITE/?post_type=app_release&searchtype=apk&s=${URLEncoder.encode(query, "UTF-8")}"
    }

    /**
     * The version as APKMirror writes it into a release address: lower case, every run of other
     * characters a single dash, no dash at either end. A leading `v` before a digit is dropped, so
     * `v21.40.161` and `21.40.161` lead to the same page. Blank input gives an empty string.
     */
    fun versionSlug(version: String): String =
        version.trim()
            .replace(LEADING_V, "")
            .lowercase(Locale.US)
            .replace(NON_SLUG_RUN, "-")
            .trim('-')

    private val LEADING_V = Regex("^[vV](?=\\d)")
    private val NON_SLUG_RUN = Regex("[^a-z0-9]+")
}
