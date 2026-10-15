/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.manager

import app.morphe.manager.util.KnownApps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApkMirrorLinksTest {
    private val site = "https://www.apkmirror.com"

    @Test
    fun releasePageWritesTheVersionWithDashes() {
        assertEquals(
            "$site/apk/google-inc/youtube/youtube-21-40-161-release/",
            ApkMirrorLinks.downloadPage(KnownApps.YOUTUBE, "21.40.161")
        )
        assertEquals(
            "$site/apk/google-inc/youtube-music/youtube-music-7-03-52-release/",
            ApkMirrorLinks.downloadPage(KnownApps.YOUTUBE_MUSIC, "7.03.52")
        )
        assertEquals(
            "$site/apk/redditinc/reddit/reddit-2026-10-0-release/",
            ApkMirrorLinks.downloadPage(KnownApps.REDDIT, "2026.10.0")
        )
    }

    @Test
    fun leadingVBeforeADigitIsDropped() {
        assertEquals(
            ApkMirrorLinks.downloadPage(KnownApps.YOUTUBE, "21.40.161"),
            ApkMirrorLinks.downloadPage(KnownApps.YOUTUBE, "v21.40.161")
        )
    }

    @Test
    fun specialCharactersInTheVersionBecomeSingleDashes() {
        assertEquals(
            "$site/apk/google-inc/youtube/youtube-1-2-beta-3-release/",
            ApkMirrorLinks.downloadPage(KnownApps.YOUTUBE, "1.2 beta+3")
        )
        assertEquals("1-2-3", ApkMirrorLinks.versionSlug("--1..2__3--"))
        assertEquals("vanilla", ApkMirrorLinks.versionSlug("vanilla"))
    }

    @Test
    fun knownAppWithoutVersionOpensItsOwnPage() {
        assertEquals("$site/apk/google-inc/youtube/", ApkMirrorLinks.downloadPage(KnownApps.YOUTUBE, null))
        assertEquals("$site/apk/google-inc/youtube/", ApkMirrorLinks.downloadPage(KnownApps.YOUTUBE, "   "))
    }

    @Test
    fun unknownPackageFallsBackToSearchWithItsVersion() {
        assertEquals(
            "$site/?post_type=app_release&searchtype=apk&s=com.example.app+1.2.3",
            ApkMirrorLinks.downloadPage("com.example.app", "1.2.3")
        )
    }

    @Test
    fun unknownPackageWithoutVersionSearchesThePackageAlone() {
        assertEquals(
            "$site/?post_type=app_release&searchtype=apk&s=com.example.app",
            ApkMirrorLinks.downloadPage("com.example.app", null)
        )
    }

    @Test
    fun searchEncodesTheQuery() {
        assertEquals(
            "$site/?post_type=app_release&searchtype=apk&s=com.example.app+1.0+%28beta%29",
            ApkMirrorLinks.search("com.example.app", "1.0 (beta)")
        )
        assertEquals(
            "$site/?post_type=app_release&searchtype=apk&s=com.example.app+1%262%2B3",
            ApkMirrorLinks.search("com.example.app", "1&2+3")
        )
    }

    @Test
    fun everyBundleAppHasAListing() {
        KnownApps.all.forEach { entry ->
            assertTrue(
                ApkMirrorLinks.KNOWN_APPS.containsKey(entry.packageName),
                "No APKMirror listing for ${entry.packageName}"
            )
        }
    }
}
