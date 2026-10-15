/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.manager

import app.morphe.manager.network.dto.GitHubAsset
import app.morphe.manager.network.dto.GitHubRelease
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OruUpdateRulesTest {

    private val repo = "https://github.com/The-FunMan/oru/releases/download"

    private fun version(text: String) = requireNotNull(OruVersion.parse(text)) { "not a version: $text" }

    private fun release(
        tag: String,
        draft: Boolean = false,
        apkName: String = oruApkName(tag),
        sumsName: String = "SHA256SUMS.txt",
        apkUrl: String = "$repo/$tag/$apkName",
        sumsUrl: String = "$repo/$tag/$sumsName",
        publishedAt: String? = "2026-10-13T10:00:00Z",
        createdAt: String? = null,
        body: String? = "Notes",
    ) = GitHubRelease(
        tagName = tag,
        body = body,
        draft = draft,
        publishedAt = publishedAt,
        createdAt = createdAt,
        assets = listOf(
            GitHubAsset(name = apkName, downloadUrl = apkUrl),
            GitHubAsset(name = sumsName, downloadUrl = sumsUrl),
        ),
    )

    private fun candidate(version: String) = OruCandidate(
        version = version,
        tag = "v$version",
        apkUrl = "$repo/v$version/oru-$version.apk",
        checksumsUrl = "$repo/v$version/SHA256SUMS.txt",
        notes = "",
        publishedAt = "2026-10-13T10:00:00Z",
    )

    // Versions

    @Test
    fun parsesStableAndDevVersions() {
        assertEquals(OruVersion(1, 0, 0, null), OruVersion.parse("1.0.0"))
        assertEquals(OruVersion(1, 0, 0, null), OruVersion.parse("v1.0.0"))
        assertEquals(OruVersion(1, 35, 1, 1), OruVersion.parse("1.35.1-dev.1"))
    }

    @Test
    fun refusesWhatIsNotAOruVersion() {
        assertNull(OruVersion.parse(""))
        assertNull(OruVersion.parse("1.0"))
        assertNull(OruVersion.parse("1.0.0-rc.1"))
        assertNull(OruVersion.parse("1.0.0-dev."))
        assertNull(OruVersion.parse("1.0.0-dev.99999999999"))
        assertNull(OruVersion.parse("1.0.0-dev.1-extra"))

        // Out of range: the bounds keep every versionCode unique and inside an Int
        assertNull(OruVersion.parse("21.0.0")) // major > 20
        assertNull(OruVersion.parse("1.100.0")) // minor > 99, runs into the major digit
        assertNull(OruVersion.parse("0.100.0")) // minor > 99
        assertNull(OruVersion.parse("1.0.1000")) // patch > 999
        assertNull(OruVersion.parse("1.0.0-dev.0")) // dev < 1
        assertNull(OruVersion.parse("1.0.0-dev.999")) // dev > 998
    }

    @Test
    fun orderedByCoreThenDevNumberWithStableAfterItsDevBuilds() {
        val ordered = listOf(
            "1.0.0-dev.2",
            "1.0.0-dev.9",
            "1.0.0-dev.10",
            "1.0.0",
            "1.0.1-dev.1",
            "1.1.0-dev.1",
            "1.10.0",
            "2.0.0-dev.1",
        ).map(::version)
        for (index in 0 until ordered.size - 1) {
            assertTrue(ordered[index] < ordered[index + 1], "${ordered[index]} should sort before ${ordered[index + 1]}")
        }
    }

    @Test
    fun versionTextRoundTrips() {
        assertEquals("1.0.0", version("v1.0.0").toString())
        assertEquals("1.35.1-dev.1", version("1.35.1-dev.1").toString())
    }

    @Test
    fun apkNameFollowsTheReleasePlan() {
        assertEquals("oru-1.0.0-dev.2.apk", oruApkName("v1.0.0-dev.2"))
        assertEquals("oru-1.0.0.apk", oruApkName("1.0.0"))
    }

    @Test
    fun deterministicVersionCodeCalculation() {
        assertEquals(1999, version("0.0.1").versionCode)
        assertEquals(1003, version("0.0.1-dev.3").versionCode)
        assertEquals(100000999, version("1.0.0").versionCode)
        assertEquals(100000001, version("1.0.0-dev.1").versionCode)
        assertEquals(101000001, version("1.1.0-dev.1").versionCode)
        assertEquals(100001999, version("1.0.1").versionCode)

        assertEquals(1999, oruVersionCode("v0.0.1"))
        assertEquals(1003, oruVersionCode("0.0.1-dev.3"))
        assertEquals(100000999, oruVersionCode("v1.0.0"))
    }

    @Test
    fun versionCodeAtTheLimitsOfTheRanges() {
        assertEquals(2099999999, version("20.99.999").versionCode)
        assertEquals(1999, version("0.0.1").versionCode)
        assertEquals(1003, version("0.0.1-dev.3").versionCode)
        assertEquals(2099999999, oruVersionCode("20.99.999"))
        assertFails { oruVersionCode("21.0.0") }
        assertFails { OruVersion(21, 0, 0, null) }
        assertFails { OruVersion(0, 100, 0, null) }
        assertFails { OruVersion(0, 0, 1000, null) }
        assertFails { OruVersion(0, 0, 0, 999) }
    }

    @Test
    fun versionCodesAreUniqueAndFollowVersionOrderAcrossTheRanges() {
        val versions = mutableListOf<OruVersion>()
        for (major in 0..20 step 5) {
            for (minor in 0..99 step 11) {
                for (patch in 0..999 step 111) {
                    val devs = listOf(1, 2, 998).map { OruVersion(major, minor, patch, it) }
                    val stable = OruVersion(major, minor, patch, null)
                    // A stable release comes after every dev build of its X.Y.Z, and devs go by their number
                    assertTrue(devs.all { it < stable }, "$stable must come after its dev builds")
                    assertTrue(devs[0] < devs[1] && devs[1] < devs[2], "dev builds of $major.$minor.$patch must be ordered")
                    versions += devs
                    versions += stable
                }
            }
        }
        assertEquals(2000, versions.size)
        assertEquals(versions.size, versions.map { it.versionCode }.toSet().size, "versionCodes must be unique")
        assertEquals(versions.sorted(), versions.sortedBy { it.versionCode }, "versionCode order must match version order")
    }

    @Test
    fun versionCodeOrderingMatchesVersionOrdering() {
        val versions = listOf(
            "0.0.1-dev.1",
            "0.0.1-dev.3",
            "0.0.1",
            "0.0.2-dev.1",
            "0.0.2",
            "1.0.0-dev.1",
            "1.0.0-dev.2",
            "1.0.0",
            "1.0.1-dev.1",
            "1.0.1",
            "1.1.0-dev.1",
            "1.1.0",
            "2.0.0-dev.1",
            "2.0.0",
        ).map(::version)

        for (i in 0 until versions.size - 1) {
            val a = versions[i]
            val b = versions[i + 1]
            assertTrue(a < b, "$a must be less than $b")
            assertTrue(a.versionCode < b.versionCode, "${a.versionCode} of $a must be less than ${b.versionCode} of $b")
        }
    }

    @Test
    fun downloadsAreOnlyAcceptedFromTheOruRepository() {
        assertTrue(isOruReleaseDownload("https://github.com/The-FunMan/oru/releases/download/v1.0.0/oru-1.0.0.apk"))
        assertFalse(isOruReleaseDownload("https://github.com/MorpheApp/morphe-manager/releases/download/v1.0.0/a.apk"))
        assertFalse(isOruReleaseDownload("http://github.com/The-FunMan/oru/releases/download/v1.0.0/a.apk"))
        assertFalse(isOruReleaseDownload("https://evil.example/The-FunMan/oru/releases/download/v1.0.0/a.apk"))
    }

    // Candidates from releases

    @Test
    fun aReleaseWithItsApkAndChecksumsIsACandidate() {
        val candidate = candidateOf(release("v1.0.0-dev.2"))
        assertEquals("1.0.0-dev.2", candidate?.version)
        assertEquals("$repo/v1.0.0-dev.2/oru-1.0.0-dev.2.apk", candidate?.apkUrl)
        assertEquals("$repo/v1.0.0-dev.2/SHA256SUMS.txt", candidate?.checksumsUrl)
        assertEquals("Notes", candidate?.notes)
    }

    @Test
    fun theChecksumListMayBeNamedWithoutItsExtension() {
        assertEquals("$repo/v1.0.0/SHA256SUMS", candidateOf(release("v1.0.0", sumsName = "SHA256SUMS"))?.checksumsUrl)
    }

    @Test
    fun aReleaseWithoutItsApkOrChecksumsIsNotACandidate() {
        assertNull(candidateOf(release("v1.0.0", apkName = "morphe-manager-1.0.0.apk")))
        assertNull(candidateOf(release("v1.0.0", sumsName = "notes.txt")))
    }

    @Test
    fun aDraftOrANonOruTagIsNotACandidate() {
        assertNull(candidateOf(release("v1.0.0", draft = true)))
        assertNull(candidateOf(release("v1.0.0-rc.1")))
        assertNull(candidateOf(release("nightly")))
    }

    @Test
    fun anAssetFromAnotherHostIsNotACandidate() {
        assertNull(candidateOf(release("v1.0.0", apkUrl = "https://example.com/oru-1.0.0.apk")))
        assertNull(candidateOf(release("v1.0.0", sumsUrl = "https://example.com/SHA256SUMS.txt")))
    }

    @Test
    fun theCreationTimeStandsInForAMissingPublicationTime() {
        val candidate = candidateOf(release("v1.0.0", publishedAt = null, createdAt = "2026-10-13T10:00:00Z"))
        assertEquals("2026-10-13T10:00:00Z", candidate?.publishedAt)
        assertNull(candidateOf(release("v1.0.0", publishedAt = null, createdAt = null)))
        assertNull(candidateOf(release("v1.0.0", publishedAt = "yesterday")))
    }

    @Test
    fun parsesAListingAndASingleReleaseFromTheirJson() {
        val asset = """{"name":"oru-1.0.0.apk","browser_download_url":"$repo/v1.0.0/oru-1.0.0.apk"}"""
        val sums = """{"name":"SHA256SUMS.txt","browser_download_url":"$repo/v1.0.0/SHA256SUMS.txt"}"""
        val release = """{"tag_name":"v1.0.0","draft":false,"prerelease":false,"published_at":"2026-10-13T10:00:00Z",""" +
                """"assets":[$asset,$sums],"unknown_field":true}"""

        assertEquals(listOf("1.0.0"), parseOruReleases("[$release]", listing = true).map { it.version })
        assertEquals(listOf("1.0.0"), parseOruReleases(release, listing = false).map { it.version })
        assertFails { parseOruReleases("<html>", listing = true) }
    }

    // Choosing the update

    @Test
    fun theStableChannelNeverOffersADevBuild() {
        val picked = pickOruUpdate(
            candidates = listOf(candidate("1.1.0-dev.1"), candidate("1.0.1")),
            installed = version("1.0.0"),
            devChannel = false,
        )
        assertEquals("1.0.1", picked?.version)
    }

    @Test
    fun theDevChannelOffersTheHighestVersionIncludingStable() {
        val picked = pickOruUpdate(
            candidates = listOf(candidate("1.0.0-dev.4"), candidate("1.0.0"), candidate("0.9.0")),
            installed = version("1.0.0-dev.1"),
            devChannel = true,
        )
        assertEquals("1.0.0", picked?.version)
    }

    @Test
    fun aStableReleaseBeatsTheDevBuildsOfItsOwnVersion() {
        assertNull(
            pickOruUpdate(
                candidates = listOf(candidate("1.0.0-dev.9")),
                installed = version("1.0.0"),
                devChannel = true,
            )
        )
    }

    @Test
    fun nothingNewerMeansNoUpdate() {
        assertNull(pickOruUpdate(listOf(candidate("1.0.0")), version("1.0.0"), devChannel = false))
        assertNull(pickOruUpdate(emptyList(), version("1.0.0"), devChannel = true))
    }

    // Checksum lists

    private val digest = "a".repeat(64)
    private val otherDigest = "b".repeat(64)

    @Test
    fun readsSha256sumLinesInBothModes() {
        val sums = parseSha256Sums("$digest  oru-1.0.0.apk\n$otherDigest *oru-1.0.1.apk\n")
        assertEquals(mapOf("oru-1.0.0.apk" to digest, "oru-1.0.1.apk" to otherDigest), sums)
    }

    @Test
    fun digestsAreReadInLowercase() {
        assertEquals(digest, parseSha256Sums("${digest.uppercase()}  oru-1.0.0.apk")?.get("oru-1.0.0.apk"))
    }

    @Test
    fun aMalformedLineMakesTheWholeListUnreadable() {
        assertNull(parseSha256Sums("$digest  oru-1.0.0.apk\nnot a digest line"))
        assertNull(parseSha256Sums("abc  oru-1.0.0.apk"))
    }

    @Test
    fun aNameListedWithTwoDigestsIsAmbiguousButARepeatedSameDigestIsNot() {
        assertNull(parseSha256Sums("$digest  oru-1.0.0.apk\n$otherDigest  oru-1.0.0.apk"))
        assertEquals(
            mapOf("oru-1.0.0.apk" to digest),
            parseSha256Sums("$digest  oru-1.0.0.apk\n$digest  oru-1.0.0.apk"),
        )
    }

    @Test
    fun anEmptyListHasNoEntries() {
        assertEquals(emptyMap(), parseSha256Sums(""))
    }

    // Signers

    @Test
    fun aMatchingSignerSetMatches() {
        assertTrue(signersMatch(setOf("aa"), setOf("aa")))
    }

    @Test
    fun anEmptySideNeverMatches() {
        assertFalse(signersMatch(emptySet(), emptySet()))
        assertFalse(signersMatch(emptySet(), setOf("aa")))
        assertFalse(signersMatch(setOf("aa"), emptySet()))
    }

    @Test
    fun aDifferentOrWiderSignerSetDoesNotMatch() {
        assertFalse(signersMatch(setOf("aa"), setOf("bb")))
        assertFalse(signersMatch(setOf("aa"), setOf("aa", "bb")))
    }
}
