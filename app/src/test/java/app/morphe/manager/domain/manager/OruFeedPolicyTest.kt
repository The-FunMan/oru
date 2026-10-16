/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.manager

import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OruFeedPolicyTest {

    private val repo = "https://github.com/The-FunMan/oru/releases/download"
    private val latest = OruFeedEndpoint("https://feed/latest", listing = false)
    private val listing = OruFeedEndpoint("https://feed/list", listing = true)

    private var now = 1_000_000_000_000L
    private val store = FakeStore()
    private val feed = FakeFeed()
    private val policy = OruFeedPolicy(feed = feed, store = store, clock = { now })

    private class FakeStore : OruFeedStore {
        var state: OruFeedState? = null
        override fun load(): OruFeedState? = state
        override fun save(state: OruFeedState) {
            this.state = state
        }
    }

    private class FakeFeed : OruReleaseFeed {
        val answers = ArrayDeque<OruFeedAnswer>()
        val requests = mutableListOf<Pair<String, String?>>()
        var thrown: Throwable? = null

        override suspend fun fetch(url: String, etag: String?): OruFeedAnswer {
            requests += url to etag
            thrown?.let { throw it }
            return answers.removeFirst()
        }
    }

    private fun releaseJson(tag: String): String {
        val version = tag.removePrefix("v")
        return """{"tag_name":"$tag","draft":false,"prerelease":false,"published_at":"2026-10-13T10:00:00Z",""" +
                """"body":"Notes","assets":[""" +
                """{"name":"oru-$version.apk","browser_download_url":"$repo/$tag/oru-$version.apk"},""" +
                """{"name":"SHA256SUMS.txt","browser_download_url":"$repo/$tag/SHA256SUMS.txt"}]}"""
    }

    private fun check(endpoint: OruFeedEndpoint = latest, force: Boolean = false): List<String> = runBlocking {
        policy.candidates(endpoint, force).map { it.version }
    }

    private fun advance(millis: Long) {
        now += millis
    }

    /** One successful check of the stable feed, which leaves 1.0.0 as the known candidate. */
    private fun seedStableRelease(etag: String? = "\"e1\"") {
        feed.answers.addLast(OruFeedAnswer.Body(releaseJson("v1.0.0"), etag))
        assertEquals(listOf("1.0.0"), check())
    }

    @Test
    fun firstCheckFetchesAndKeepsTheCandidatesAndTheEtag() {
        seedStableRelease()

        assertEquals(listOf<Pair<String, String?>>(latest.url to null), feed.requests)
        assertEquals("\"e1\"", store.state?.etag)
    }

    @Test
    fun aCheckWithinTheIntervalMakesNoRequest() {
        seedStableRelease()
        advance(OruFeedPolicy.CHECK_INTERVAL_MILLIS - 1)

        assertEquals(listOf("1.0.0"), check())
        assertEquals(1, feed.requests.size)
    }

    @Test
    fun aCheckAfterTheIntervalSendsTheEtagAndAnUnchangedAnswerKeepsTheCandidates() {
        seedStableRelease()
        advance(OruFeedPolicy.CHECK_INTERVAL_MILLIS)
        feed.answers.addLast(OruFeedAnswer.NotModified)

        assertEquals(listOf("1.0.0"), check())
        assertEquals("\"e1\"", feed.requests.last().second)
        assertEquals(now, store.state?.attemptedAtMillis)
    }

    @Test
    fun aForcedCheckIgnoresTheInterval() {
        seedStableRelease()
        advance(1_000)
        feed.answers.addLast(OruFeedAnswer.NotModified)

        check(force = true)
        assertEquals(2, feed.requests.size)
    }

    @Test
    fun aFailedCheckKeepsThePreviousResultAndWaitsForTheNextInterval() {
        seedStableRelease()
        advance(OruFeedPolicy.CHECK_INTERVAL_MILLIS)
        feed.answers.addLast(OruFeedAnswer.Failed("HTTP 500"))

        assertEquals(listOf("1.0.0"), check())
        advance(60_000)
        assertEquals(listOf("1.0.0"), check())
        assertEquals(2, feed.requests.size)
    }

    @Test
    fun aThrownRequestIsAFailureAndKeepsThePreviousResult() {
        seedStableRelease()
        advance(OruFeedPolicy.CHECK_INTERVAL_MILLIS)
        feed.thrown = IOException("offline")

        assertEquals(listOf("1.0.0"), check())
    }

    @Test
    fun cancellationIsNotSwallowedAsAFailure() {
        feed.thrown = CancellationException("stopped")

        assertFailsWith<CancellationException> { check() }
    }

    @Test
    fun aBodyThatDoesNotParseKeepsThePreviousResult() {
        seedStableRelease()
        advance(OruFeedPolicy.CHECK_INTERVAL_MILLIS)
        feed.answers.addLast(OruFeedAnswer.Body("<html>", etag = "\"e2\""))

        assertEquals(listOf("1.0.0"), check())
        assertEquals("\"e1\"", store.state?.etag)
    }

    @Test
    fun aMissingReleaseMeansNoCandidates() {
        seedStableRelease()
        advance(OruFeedPolicy.CHECK_INTERVAL_MILLIS)
        feed.answers.addLast(OruFeedAnswer.NotFound)

        assertEquals(emptyList(), check())
    }

    @Test
    fun aRateLimitHoldsEvenForcedChecksUntilGitHubResets() {
        feed.answers.addLast(OruFeedAnswer.RateLimited(resetAtSeconds = now / 1000 + 600, retryAfterSeconds = null))
        assertEquals(emptyList(), check())

        advance(60_000)
        check(force = true)
        assertEquals(1, feed.requests.size)

        advance(600_000)
        feed.answers.addLast(OruFeedAnswer.NotFound)
        check(force = true)
        assertEquals(2, feed.requests.size)
    }

    @Test
    fun aRateLimitWithoutAResetWaitsForRetryAfter() {
        feed.answers.addLast(OruFeedAnswer.RateLimited(resetAtSeconds = null, retryAfterSeconds = 120))
        check()

        advance(60_000)
        check(force = true)
        assertEquals(1, feed.requests.size)
    }

    @Test
    fun aRateLimitHoldIsCappedAtAnHour() {
        feed.answers.addLast(OruFeedAnswer.RateLimited(resetAtSeconds = now / 1000 + 10 * 24 * 3600, retryAfterSeconds = null))
        check()

        advance(OruFeedPolicy.MAX_BLOCK_MILLIS + 1)
        feed.answers.addLast(OruFeedAnswer.NotFound)
        check(force = true)
        assertEquals(2, feed.requests.size)
    }

    @Test
    fun aChannelChangeFetchesTheOtherFeedWithoutItsEtag() {
        seedStableRelease()
        feed.answers.addLast(OruFeedAnswer.Body("[${releaseJson("v1.1.0-dev.1")}]", etag = "\"d1\""))

        assertEquals(listOf("1.1.0-dev.1"), check(listing))
        assertEquals(listing.url to null, feed.requests.last())
    }

    @Test
    fun aClockThatWentBackwardsDoesNotFreezeTheFeed() {
        seedStableRelease()
        now -= 10 * OruFeedPolicy.CHECK_INTERVAL_MILLIS
        feed.answers.addLast(OruFeedAnswer.NotModified)

        check()
        assertEquals(2, feed.requests.size)
    }
}
