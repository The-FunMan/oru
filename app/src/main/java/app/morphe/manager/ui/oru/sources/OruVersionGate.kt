/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.sources

import app.morphe.manager.domain.bundles.BundledAppTarget
import app.morphe.manager.domain.bundles.patchableAt
import app.morphe.manager.util.compareVersions

/**
 * Where one version of an app stands with the source it is patched from. The Home card and the Focus both read it from
 * [oruVersionGate], so a card never says an app is ready to patch from a version the Focus would stop.
 */
enum class OruVersionGate {
    /** The source patches this version. */
    Supported,

    /** Marked experimental, and the source has experimental versions on. The Focus asks once before it patches. */
    ExperimentalAllowed,

    /** Marked experimental, and the source has experimental versions off. It is not patched. */
    ExperimentalOff,

    /** The source does not patch this version. */
    Unsupported;

    /** Whether a card may say the app is ready to patch from this version. An allowed experimental version is one. */
    val patchable: Boolean get() = this == Supported || this == ExperimentalAllowed
}

/** Which way an unsupported version is off, read against the newest version the source patches. */
enum class OruVersionSide { Newer, Older, Unknown }

/**
 * The gate of [version] (and its build code [versionCode], when known) against [targets]: the versions of the source the
 * app is patched from (`OruAppSources.effectiveVersions`). Pure, so the card and the Focus share one answer.
 *
 * A missing version, or a source with no version data at all, is not judged and passes.
 */
fun oruVersionGate(version: String?, versionCode: Long?, targets: List<BundledAppTarget>): OruVersionGate {
    if (version.isNullOrBlank()) return OruVersionGate.Supported
    if (!targets.patchableAt(version, versionCode)) return OruVersionGate.Unsupported
    val matching = targets.filter { it.target.version == version }
    if (matching.none { it.target.isExperimental }) return OruVersionGate.Supported
    return if (matching.any { it.experimentalEnabled }) OruVersionGate.ExperimentalAllowed else OruVersionGate.ExperimentalOff
}

/**
 * Which way [version] is off, against [supported], the newest version the source patches. Unknown when there is no
 * such version to compare with.
 */
fun oruVersionSide(version: String, supported: String?): OruVersionSide {
    if (supported.isNullOrBlank()) return OruVersionSide.Unknown
    val order = compareVersions(version, supported)
    return when {
        order > 0 -> OruVersionSide.Newer
        order < 0 -> OruVersionSide.Older
        else -> OruVersionSide.Unknown
    }
}
