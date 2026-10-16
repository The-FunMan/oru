/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.manager

import app.morphe.manager.util.KnownApps
import kotlinx.coroutines.runBlocking
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadUrlResolverFallbackTest {
    private val abi = "arm64-v8a"

    private fun searchUrl(query: String) =
        "https://google.com/search?q=${URLEncoder.encode(query, "UTF-8")}&udm=14"

    private val sites = "(site:apkmirror.com OR site:uptodown.com OR site:apkpure.com OR site:apkcombo.com)"

    @Test
    fun noNetworkReturnsWebSearchForAppAndVersion() = runBlocking {
        val chain = DownloadUrlChain(redirect = { null }, abi = abi)

        val url = chain.resolve("com.reddit.frontpage", "2026.10.0")

        assertEquals(
            searchUrl("\"com.reddit.frontpage\" \"2026.10.0\" nodpi $sites"),
            url
        )
    }

    @Test
    fun noNetworkWithoutVersionLeavesTheVersionOut() = runBlocking {
        val chain = DownloadUrlChain(redirect = { null }, abi = abi)

        val url = chain.resolve("com.google.android.youtube", null)

        assertEquals(
            searchUrl("\"com.google.android.youtube\"  nodpi $sites"),
            url
        )
    }

    @Test
    fun youtubeMusicSearchUsesTheDeviceAbi() {
        val chain = DownloadUrlChain(redirect = { null }, abi = abi)

        val url = chain.webSearchUrl(KnownApps.YOUTUBE_MUSIC, "7.00.00")

        assertEquals(
            searchUrl("\"${KnownApps.YOUTUBE_MUSIC}\" \"7.00.00\"  ($abi) $sites"),
            url
        )
    }

    @Test
    fun apiLookupFollowsRedirectsToTheDownloadPage() = runBlocking {
        val download = "https://www.apkmirror.com/apk/example/app-1-2-3-android-apk-download/"
        // The API answers the search with its own address once more, then the mirror's page
        val chain = DownloadUrlChain(
            redirect = { url ->
                when {
                    url.contains("/v2/web-search/") -> "https://api.morphe.software/hop"
                    url == "https://api.morphe.software/hop" -> download
                    else -> null
                }
            },
            abi = abi
        )

        val url = chain.resolve("com.reddit.frontpage", "1.2.3")

        assertEquals(download, url)
    }

    @Test
    fun apiSearchUrlNamesThePackageVersionAndAbi() {
        val chain = DownloadUrlChain(redirect = { null }, abi = abi)

        val url = chain.apiSearchUrl("com.reddit.frontpage", "1.2.3")

        assertTrue(url.startsWith("https://api.morphe.software/v2/web-search/"))
        assertTrue(url.contains("com.reddit.frontpage"))
        assertTrue(url.contains("1.2.3"))
        assertTrue(url.contains("arm64-v8a"))
    }
}
