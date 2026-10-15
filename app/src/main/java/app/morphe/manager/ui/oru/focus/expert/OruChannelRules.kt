/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus.expert

import app.morphe.manager.domain.bundles.APIPatchBundle
import app.morphe.manager.domain.bundles.JsonPatchBundle
import app.morphe.manager.domain.bundles.PatchBundleSource

/** A patch source that covers the app, as the channel rows read it. */
data class OruChannelSource(
    val uid: Int,
    val name: String,
    val usesPrerelease: Boolean,
    val preReleaseSupported: Boolean,
)

/** The channel row of one source. Pre-release is on only when the source supports it and has it switched on. */
data class OruChannelState(
    val uid: Int,
    val name: String,
    val preRelease: Boolean,
    val preReleaseAvailable: Boolean,
)

/**
 * Whether a source can be switched to pre-release. The same rule the Advanced sources list applies: API sources
 * always can, a JSON source only when its endpoint names a branch (`JsonPatchBundle.supportsPrerelease`).
 */
fun oruPreReleaseSupported(source: PatchBundleSource): Boolean =
    source is APIPatchBundle || (source is JsonPatchBundle && source.supportsPrerelease)

/** One channel row per source, in the order given. A source that cannot offer pre-release shows stable only. */
fun oruChannelStates(sources: List<OruChannelSource>): List<OruChannelState> = sources.map { source ->
    OruChannelState(
        uid = source.uid,
        name = source.name,
        preRelease = source.usesPrerelease && source.preReleaseSupported,
        preReleaseAvailable = source.preReleaseSupported,
    )
}

/** What the Patch channel row of an app says: stable, pre-release, or how many of its sources are on pre-release. */
sealed interface OruChannelSummary {
    data object Stable : OruChannelSummary
    data object PreRelease : OruChannelSummary
    data class Mixed(val preRelease: Int, val sources: Int) : OruChannelSummary
}

/** The summary of the channels of the sources that cover an app, or null when none covers it. */
fun oruChannelSummary(channels: List<OruChannelState>): OruChannelSummary? {
    if (channels.isEmpty()) return null
    val preRelease = channels.count { it.preRelease }
    return when (preRelease) {
        0 -> OruChannelSummary.Stable
        channels.size -> OruChannelSummary.PreRelease
        else -> OruChannelSummary.Mixed(preRelease, channels.size)
    }
}
