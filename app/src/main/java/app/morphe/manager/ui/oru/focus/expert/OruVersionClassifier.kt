/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus.expert

/** One version the sources cover for an app, as the version sheet reads it. */
data class OruVersionCandidate(
    val version: String,
    val isExperimental: Boolean,
    /** The source this version comes from is switched into experimental mode. */
    val experimentalEnabled: Boolean,
    /** The phone meets the minimum SDK the version declares. */
    val installable: Boolean,
)

/** Where a version stands, in the order the sheet lists them. */
enum class OruVersionKind { Recommended, Compatible, Experimental, Unsupported }

data class OruVersionEntry(
    val version: String,
    val kind: OruVersionKind,
    val isExperimental: Boolean,
    /** Experimental, while the source has experimental versions switched off. */
    val experimentalOff: Boolean,
) {
    /** An unsupported version cannot be installed on this phone, so it is listed but cannot be chosen. */
    val selectable: Boolean get() = kind != OruVersionKind.Unsupported
}

/**
 * Orders and marks the versions of an app: the recommended one first, then the compatible ones, then the
 * experimental ones, and the unsupported ones last. Pure, so the sheet and the tests share one answer.
 */
object OruVersionClassifier {

    fun classify(candidates: List<OruVersionCandidate>, recommended: String?): List<OruVersionEntry> {
        val byVersion = LinkedHashMap<String, OruVersionCandidate>()
        for (candidate in candidates) {
            val kept = byVersion[candidate.version]
            // A version one source lists as stable is stable, whatever another source says about it
            if (kept == null || (kept.isExperimental && !candidate.isExperimental)) {
                byVersion[candidate.version] = candidate
            }
        }
        return byVersion.values
            .map { candidate ->
                OruVersionEntry(
                    version = candidate.version,
                    kind = kindOf(candidate, recommended),
                    isExperimental = candidate.isExperimental,
                    experimentalOff = candidate.isExperimental && !candidate.experimentalEnabled,
                )
            }
            // sortedBy is stable, so each group keeps the order the sources listed it in (newest first)
            .sortedBy { it.kind.ordinal }
    }

    private fun kindOf(candidate: OruVersionCandidate, recommended: String?): OruVersionKind = when {
        candidate.version == recommended -> OruVersionKind.Recommended
        !candidate.installable -> OruVersionKind.Unsupported
        candidate.isExperimental -> OruVersionKind.Experimental
        else -> OruVersionKind.Compatible
    }
}
