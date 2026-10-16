/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.util

import app.morphe.manager.domain.bundles.PatchBundleSource
import app.morphe.manager.domain.repository.PatchBundleRepository.Companion.DEFAULT_SOURCE_UID

/**
 * ORU: the names patch sources are shown under.
 *
 * The default source is stored with the upstream name, and that stored name is left alone: a
 * migration would be needed to change it, and exports and existing data carry it. Only what the
 * app shows changes, so the default source reads as [DEFAULT_LABEL].
 */
object OruSourceNames {
    /** Neutral name for the default patch source. */
    const val DEFAULT_LABEL = "Default patches"

    /**
     * The name to show for a source stored as [sourceName]. [oruUx] is true in the app;
     * tests pass it explicitly.
     */
    fun displayName(
        sourceName: String,
        isDefault: Boolean,
        oruUx: Boolean = true,
    ): String = if (oruUx && isDefault) DEFAULT_LABEL else sourceName
}

/**
 * ORU: the title a source is shown under. Same as [PatchBundleSource.displayTitle] except that the
 * default source reads as [OruSourceNames.DEFAULT_LABEL].
 */
val PatchBundleSource.shownTitle: String
    get() = OruSourceNames.displayName(displayTitle, isDefault = uid == DEFAULT_SOURCE_UID)
