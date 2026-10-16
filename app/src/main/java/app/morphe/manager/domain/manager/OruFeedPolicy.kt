/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.manager

import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException

/** What a feed request came back with. */
sealed interface OruFeedAnswer {
    /** A new body. [etag] is the validator to send with the next request for the same URL, if the feed gave one. */
    data class Body(val text: String, val etag: String?) : OruFeedAnswer

    /** The body is unchanged since the ETag that was sent. */
    data object NotModified : OruFeedAnswer

    /** The repository lists no such release. */
    data object NotFound : OruFeedAnswer

    /**
     * GitHub refused the request for its rate limit. [resetAtSeconds] is the epoch second the limit lifts
     * and [retryAfterSeconds] a relative wait, each when the response gave it.
     */
    data class RateLimited(val resetAtSeconds: Long?, val retryAfterSeconds: Long?) : OruFeedAnswer

    /** Any other failure. The previous result stays in use. */
    data class Failed(val reason: String) : OruFeedAnswer
}

/** Fetches a feed URL. [etag], when not null, goes out as If-None-Match. */
interface OruReleaseFeed {
    suspend fun fetch(url: String, etag: String?): OruFeedAnswer
}

/** One feed URL and whether its answer is a list of releases or a single one. */
data class OruFeedEndpoint(val url: String, val listing: Boolean)

/** What the policy keeps between checks, for the URL it was last asked about. */
@Serializable
data class OruFeedState(
    val url: String,
    /** The ETag of the body in [candidates]. Null when the feed gave none, so no conditional request is made. */
    val etag: String? = null,
    val candidates: List<OruCandidate> = emptyList(),
    val attemptedAtMillis: Long = 0L,
    val blockedUntilMillis: Long = 0L,
)

/** Keeps the [OruFeedState] across runs of the app. */
interface OruFeedStore {
    fun load(): OruFeedState?
    fun save(state: OruFeedState)
}

/**
 * The rules for asking the release feed: a check runs at most once per [CHECK_INTERVAL_MILLIS] unless it is
 * forced, an unchanged feed answers 304 and costs nothing, a rate-limited feed is left alone until GitHub
 * lifts the limit, and any failure keeps the candidates of the last good answer.
 *
 * [clock] returns epoch milliseconds, so the rules can be driven by a fake clock in tests.
 */
class OruFeedPolicy(
    private val feed: OruReleaseFeed,
    private val store: OruFeedStore,
    private val clock: () -> Long,
) {
    /**
     * The candidates known for [endpoint]. A check of the feed happens only when [force] is set or the last
     * check is older than [CHECK_INTERVAL_MILLIS]. Otherwise, or when the check fails, the candidates of the last
     * good answer for that URL are returned.
     */
    suspend fun candidates(endpoint: OruFeedEndpoint, force: Boolean): List<OruCandidate> {
        val now = clock()
        val saved = store.load()?.takeIf { it.url == endpoint.url }
        val known = saved?.candidates.orEmpty()

        if (saved != null) {
            // Held back even for a forced check, since the answer would only be another refusal
            if (saved.blockedUntilMillis > now) return known
            val sinceAttempt = now - saved.attemptedAtMillis
            if (!force && sinceAttempt in 0L until CHECK_INTERVAL_MILLIS) return known
        }

        val state = saved ?: OruFeedState(url = endpoint.url)
        val answer = try {
            feed.fetch(endpoint.url, state.etag)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            OruFeedAnswer.Failed(error.message.orEmpty())
        }

        return when (answer) {
            is OruFeedAnswer.Body -> {
                val parsed = runCatching { parseOruReleases(answer.text, endpoint.listing) }.getOrNull()
                if (parsed == null) {
                    store.save(state.copy(attemptedAtMillis = now))
                    known
                } else {
                    store.save(
                        OruFeedState(
                            url = endpoint.url,
                            etag = answer.etag,
                            candidates = parsed,
                            attemptedAtMillis = now,
                        )
                    )
                    parsed
                }
            }

            OruFeedAnswer.NotModified -> {
                store.save(state.copy(attemptedAtMillis = now))
                known
            }

            OruFeedAnswer.NotFound -> {
                store.save(OruFeedState(url = endpoint.url, attemptedAtMillis = now))
                emptyList()
            }

            is OruFeedAnswer.RateLimited -> {
                store.save(state.copy(attemptedAtMillis = now, blockedUntilMillis = blockedUntil(answer, now)))
                known
            }

            is OruFeedAnswer.Failed -> {
                store.save(state.copy(attemptedAtMillis = now))
                known
            }
        }
    }

    /**
     * When a rate-limited feed may be asked again. GitHub's own reset time is used when it gave one, clamped so
     * a skewed server clock can neither block for long nor let the next request go out at once.
     */
    private fun blockedUntil(answer: OruFeedAnswer.RateLimited, now: Long): Long {
        val fromReset = answer.resetAtSeconds?.let { it * 1000L }
        val fromRetry = answer.retryAfterSeconds?.let { now + it * 1000L }
        val wanted = fromReset ?: fromRetry ?: (now + RATE_LIMIT_FALLBACK_MILLIS)
        return wanted.coerceIn(now + MIN_BLOCK_MILLIS, now + MAX_BLOCK_MILLIS)
    }

    companion object {
        /** The shortest time between two checks of the feed that are not forced. */
        const val CHECK_INTERVAL_MILLIS = 6L * 60 * 60 * 1000

        /** The longest a rate-limited feed is left alone. GitHub's limit window is an hour. */
        const val MAX_BLOCK_MILLIS = 60L * 60 * 1000

        private const val MIN_BLOCK_MILLIS = 60L * 1000
        private const val RATE_LIMIT_FALLBACK_MILLIS = 15L * 60 * 1000
    }
}
