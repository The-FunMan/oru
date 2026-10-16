/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.network.api

import android.app.Application
import android.content.Context
import android.util.Log
import app.morphe.manager.BuildConfig
import app.morphe.manager.domain.manager.OruCandidate
import app.morphe.manager.domain.manager.OruFeedAnswer
import app.morphe.manager.domain.manager.OruFeedEndpoint
import app.morphe.manager.domain.manager.OruFeedPolicy
import app.morphe.manager.domain.manager.OruFeedState
import app.morphe.manager.domain.manager.OruFeedStore
import app.morphe.manager.domain.manager.OruReleaseFeed
import app.morphe.manager.domain.manager.OruVersion
import app.morphe.manager.domain.manager.ORU_LATEST_FEED_URL
import app.morphe.manager.domain.manager.ORU_RELEASES_FEED_URL
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.manager.pickOruUpdate
import app.morphe.manager.network.dto.MorpheAsset
import app.morphe.manager.network.service.HttpService
import app.morphe.manager.util.ORU_REPO_URL
import app.morphe.manager.util.tag
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * The self-update feed of a Oru build: the releases of [app.morphe.manager.util.ORU_RELEASE_REPO], read
 * over GitHub's REST API without credentials.
 *
 * The stable channel asks for the latest release. The dev channel, which the owner's pre-release switch turns
 * on, asks for the newest releases and takes the highest version, so a stable release still reaches a dev user.
 * Every answer goes through [OruFeedPolicy], which keeps the feed quiet between checks and keeps the last
 * good result when a check fails.
 */
class OruUpdateSource(
    app: Application,
    private val http: HttpService,
    private val prefs: PreferencesManager,
) {
    private val policy = OruFeedPolicy(
        feed = GitHubReleaseFeed(http),
        store = PreferencesFeedStore(app),
        clock = { System.currentTimeMillis() },
    )

    /**
     * The newer Oru release on the channel the owner picked, or null when there is none known. [force] is a
     * manual check, which skips the interval between checks but not a rate-limit hold.
     */
    suspend fun getAppUpdate(force: Boolean): MorpheAsset? {
        val installed = OruVersion.parse(BuildConfig.VERSION_NAME) ?: run {
            Log.w(tag, "Oru version ${BuildConfig.VERSION_NAME} is not an Oru version, no update is offered")
            return null
        }
        val devChannel = prefs.useManagerPrereleases.get()
        val endpoint = if (devChannel) {
            OruFeedEndpoint(ORU_RELEASES_FEED_URL, listing = true)
        } else {
            OruFeedEndpoint(ORU_LATEST_FEED_URL, listing = false)
        }

        val candidates = withContext(Dispatchers.IO) { policy.candidates(endpoint, force) }
        return pickOruUpdate(candidates, installed, devChannel)?.toMorpheAsset()
    }

    private fun OruCandidate.toMorpheAsset() = MorpheAsset(
        downloadUrl = apkUrl,
        createdAt = Instant.parse(publishedAt).toLocalDateTime(TimeZone.UTC),
        pageUrl = "$ORU_REPO_URL/releases/tag/$tag",
        description = notes,
        version = "v$version",
        checksumsUrl = checksumsUrl,
    )
}

/**
 * Reads the feed through the raw HTTP client. [HttpService.request] discards the response headers, and the
 * ETag and the rate-limit headers are what this feed runs on.
 */
private class GitHubReleaseFeed(private val http: HttpService) : OruReleaseFeed {
    override suspend fun fetch(url: String, etag: String?): OruFeedAnswer {
        val response: HttpResponse = http.http.request {
            url(url)
            header(HttpHeaders.Accept, "application/vnd.github+json")
            etag?.let { header(HttpHeaders.IfNoneMatch, it) }
        }
        val status = response.status
        return when {
            status == HttpStatusCode.NotModified -> OruFeedAnswer.NotModified
            status == HttpStatusCode.NotFound -> OruFeedAnswer.NotFound
            status.isSuccess() -> OruFeedAnswer.Body(
                text = response.bodyAsText(),
                etag = response.headers[HttpHeaders.ETag],
            )
            isRateLimited(response) -> OruFeedAnswer.RateLimited(
                resetAtSeconds = response.headers[RATE_LIMIT_RESET_HEADER]?.toLongOrNull(),
                retryAfterSeconds = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull(),
            )
            else -> {
                Log.w(tag, "Oru release feed answered HTTP ${status.value}")
                OruFeedAnswer.Failed("HTTP ${status.value}")
            }
        }
    }

    /** GitHub answers an exhausted limit with 403 or 429, and says so in its headers. */
    private fun isRateLimited(response: HttpResponse): Boolean {
        val status = response.status
        if (status != HttpStatusCode.Forbidden && status != HttpStatusCode.TooManyRequests) return false
        return status == HttpStatusCode.TooManyRequests ||
                response.headers[RATE_LIMIT_REMAINING_HEADER] == "0" ||
                response.headers[HttpHeaders.RetryAfter] != null
    }

    private companion object {
        const val RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining"
        const val RATE_LIMIT_RESET_HEADER = "X-RateLimit-Reset"
    }
}

/** Keeps the feed state in the app's private preferences. A state that cannot be read counts as none. */
private class PreferencesFeedStore(private val app: Application) : OruFeedStore {
    private val json = Json { ignoreUnknownKeys = true }

    private val prefs by lazy { app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    override fun load(): OruFeedState? = runCatching {
        prefs.getString(STATE_KEY, null)?.let { json.decodeFromString<OruFeedState>(it) }
    }.onFailure { Log.w(tag, "Could not read the Oru update feed state", it) }.getOrNull()

    override fun save(state: OruFeedState) {
        runCatching {
            prefs.edit().putString(STATE_KEY, json.encodeToString(state)).apply()
        }.onFailure { Log.w(tag, "Could not save the Oru update feed state", it) }
    }

    private companion object {
        const val PREFS_NAME = "oru_update_feed"
        const val STATE_KEY = "state"
    }
}
