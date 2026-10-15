/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.manager

import app.morphe.manager.network.dto.GitHubRelease
import app.morphe.manager.util.ORU_RELEASE_REPO
import app.morphe.manager.util.ORU_REPO_URL
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/** Where a Oru build finds its releases: the newest stable one, or the whole list for the dev channel. */
const val ORU_LATEST_FEED_URL = "https://api.github.com/repos/$ORU_RELEASE_REPO/releases/latest"
const val ORU_RELEASES_FEED_URL = "https://api.github.com/repos/$ORU_RELEASE_REPO/releases?per_page=30"

/** The checksum lists a release may carry. The first name found on the release is used. */
val ORU_CHECKSUM_FILE_NAMES = listOf("SHA256SUMS.txt", "SHA256SUMS")

/**
 * A Oru version: `X.Y.Z` for a stable release or `X.Y.Z-dev.N` for a dev build. Anything else, such as
 * the tag of a build from another numbering, is not a Oru version and is never offered.
 */
data class OruVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    /** The dev build number, or null for a stable release. */
    val dev: Int?,
) : Comparable<OruVersion> {

    init {
        require(inRange(major, minor, patch, dev)) {
            "Not encodable as a Oru versionCode: $major.$minor.$patch dev $dev"
        }
    }

    val isDev: Boolean get() = dev != null

    /**
     * Ordered by X.Y.Z first. A stable release comes after every dev build of the same X.Y.Z, and dev builds
     * compare by their number.
     */
    override fun compareTo(other: OruVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        val mine = dev
        val theirs = other.dev
        return when {
            mine == theirs -> 0
            mine == null -> 1
            theirs == null -> -1
            else -> mine.compareTo(theirs)
        }
    }

    /**
     * Deterministic Android versionCode:
     * `major * 100_000_000 + minor * 1_000_000 + patch * 1_000 + (dev ?: 999)`. The constructor keeps the parts in
     * range, so the code is unique and always fits an Int (2099999999 at most).
     */
    val versionCode: Int
        get() = major * 100_000_000 + minor * 1_000_000 + patch * 1_000 + (dev ?: 999)

    override fun toString(): String = "$major.$minor.$patch" + (dev?.let { "-dev.$it" }.orEmpty())

    companion object {
        /**
         * The bounds that keep [versionCode] unique and inside an Int: a minor above 99 runs into the major digit,
         * and a major above 20 lets a large minor or patch overflow (21.99.999 is 2199999999). The build script
         * enforces the same bounds.
         */
        const val MAX_MAJOR = 20
        const val MAX_MINOR = 99
        const val MAX_PATCH = 999
        const val MAX_DEV = 998

        private val PATTERN = Regex("""(\d+)\.(\d+)\.(\d+)(?:-dev\.(\d+))?""")

        private fun inRange(major: Int, minor: Int, patch: Int, dev: Int?): Boolean =
            major in 0..MAX_MAJOR && minor in 0..MAX_MINOR && patch in 0..MAX_PATCH &&
                (dev == null || dev in 1..MAX_DEV)

        /** Parses `X.Y.Z` or `X.Y.Z-dev.N`, with an optional leading `v`. Null for anything else or out of range. */
        fun parse(text: String): OruVersion? {
            val groups = PATTERN.matchEntire(text.removePrefix("v"))?.groupValues ?: return null
            val major = groups[1].toIntOrNull() ?: return null
            val minor = groups[2].toIntOrNull() ?: return null
            val patch = groups[3].toIntOrNull() ?: return null
            // An absent dev group is empty; a present one must be a number, and is checked against the range below
            val dev = if (groups[4].isEmpty()) {
                null
            } else {
                groups[4].toIntOrNull() ?: return null
            }
            if (!inRange(major, minor, patch, dev)) return null
            return OruVersion(major, minor, patch, dev)
        }
    }
}

/**
 * Computes the deterministic versionCode for a Oru version string (`X.Y.Z` or `X.Y.Z-dev.N`),
 * following `major * 100_000_000 + minor * 1_000_000 + patch * 1_000 + s` where `s` is `dev ?: 999`.
 */
fun oruVersionCode(versionText: String): Int {
    val version = requireNotNull(OruVersion.parse(versionText)) {
        "Cannot compute versionCode for non-Oru version: $versionText"
    }
    return version.versionCode
}

/** The APK file name a Oru release of [version] carries, such as `oru-1.0.0-dev.2.apk`. */
fun oruApkName(version: String): String = "oru-${version.removePrefix("v")}.apk"

/** Whether [url] is a download link of a file on a release of [ORU_RELEASE_REPO]. Any other host is refused. */
fun isOruReleaseDownload(url: String): Boolean =
    url.startsWith("$ORU_REPO_URL/releases/download/", ignoreCase = true)

/**
 * A release a Oru build could install: its tag is a Oru version, and it carries the APK named for that
 * version and a checksum list, both on [ORU_RELEASE_REPO].
 */
@Serializable
data class OruCandidate(
    /** The version without its `v`, such as `1.0.0` or `1.1.0-dev.2`. */
    val version: String,
    val tag: String,
    val apkUrl: String,
    val checksumsUrl: String,
    val notes: String,
    /** When the release was published, as an ISO-8601 instant. */
    val publishedAt: String,
)

private val feedJson = Json { ignoreUnknownKeys = true }

/**
 * The candidates in a feed answer. A [listing] is a JSON array of releases, the latest feed a single
 * release. Throws when the body is not the JSON that was asked for.
 */
fun parseOruReleases(body: String, listing: Boolean): List<OruCandidate> {
    val releases = if (listing) {
        feedJson.decodeFromString<List<GitHubRelease>>(body)
    } else {
        listOf(feedJson.decodeFromString<GitHubRelease>(body))
    }
    return releases.mapNotNull(::candidateOf)
}

/** The candidate a release makes, or null when the release cannot be installed by Oru. */
internal fun candidateOf(release: GitHubRelease): OruCandidate? {
    if (release.draft) return null
    val version = OruVersion.parse(release.tagName)?.toString() ?: return null

    val apk = release.assets.firstOrNull { it.name == oruApkName(version) } ?: return null
    val checksums = ORU_CHECKSUM_FILE_NAMES.firstNotNullOfOrNull { name ->
        release.assets.firstOrNull { it.name == name }
    } ?: return null
    if (!isOruReleaseDownload(apk.downloadUrl) || !isOruReleaseDownload(checksums.downloadUrl)) return null

    val published = release.publishedAt ?: release.createdAt ?: return null
    if (runCatching { Instant.parse(published) }.isFailure) return null

    return OruCandidate(
        version = version,
        tag = release.tagName,
        apkUrl = apk.downloadUrl,
        checksumsUrl = checksums.downloadUrl,
        notes = release.body.orEmpty(),
        publishedAt = published,
    )
}

/**
 * The newest candidate newer than [installed] on the channel. The stable channel never offers a dev build,
 * and the dev channel offers stable releases too, so a dev user is not left behind by a stable one.
 */
fun pickOruUpdate(
    candidates: List<OruCandidate>,
    installed: OruVersion,
    devChannel: Boolean,
): OruCandidate? {
    val ranked = candidates.mapNotNull { candidate ->
        val version = OruVersion.parse(candidate.version) ?: return@mapNotNull null
        if (!devChannel && version.isDev) null else candidate to version
    }
    return ranked
        .filter { (_, version) -> version > installed }
        .maxByOrNull { (_, version) -> version }
        ?.first
}

private val SHA256_LINE = Regex("""([0-9a-fA-F]{64})[ \t]+\*?(.+)""")

/**
 * The entries of a `sha256sum` list, keyed by file name with lowercase digests. Null when a line does not read
 * as an entry, or a name is listed twice with different digests, so an ambiguous list checks nothing.
 */
fun parseSha256Sums(text: String): Map<String, String>? {
    val sums = linkedMapOf<String, String>()
    for (raw in text.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        val match = SHA256_LINE.matchEntire(line) ?: return null
        val digest = match.groupValues[1].lowercase()
        val name = match.groupValues[2].trim()
        val known = sums[name]
        if (known != null && known != digest) return null
        sums[name] = digest
    }
    return sums
}

/**
 * Whether an APK is signed by exactly the certificates the installed app is signed by. Empty on either side
 * never matches, so an unreadable certificate cannot pass for a matching one.
 */
fun signersMatch(archive: Set<String>, installed: Set<String>): Boolean =
    archive.isNotEmpty() && archive == installed
