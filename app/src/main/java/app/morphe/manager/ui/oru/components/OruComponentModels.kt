/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import android.content.pm.PackageInfo
import androidx.compose.runtime.Immutable
import app.morphe.manager.ui.oru.theme.OruBlobShape

/** Status tone of a chip, notice or dialog. Each tone maps to one Oru status role. */
enum class OruTone { Neutral, Ok, Warn, Error }

/** How a button is filled. A surface has one [Filled] action at most. */
enum class OruButtonStyle { Filled, Tonal, Outlined }

/**
 * What an app tile shows. [name] is always set, it is the accessible label. [packageInfo] is set only
 * when the app is installed and its real icon can be drawn. Otherwise the tile is a neutral letter.
 */
@Immutable
data class OruTileSource(
    val name: String,
    val packageInfo: PackageInfo? = null,
)

@Immutable
data class OruChipUi(
    val text: String,
    val tone: OruTone = OruTone.Neutral,
    val mono: Boolean = false,
)

@Immutable
data class OruCardButton(
    val label: String,
    val style: OruButtonStyle,
)

/**
 * One Home card. Every label comes from the caller, so the components stay free of string resources.
 * [shape] is the blob preset of the tile and [sharedKey] the shared-bounds key it morphs under.
 */
@Immutable
data class OruCardUi(
    val id: String,
    val tile: OruTileSource,
    val shape: OruBlobShape,
    val sharedKey: String?,
    val benefit: String,
    val chips: List<OruChipUi>,
    /** The source chip: the name of the source the app is patched from. Tapping it opens the choice. Null for none. */
    val source: String?,
    val hint: String?,
    val primary: OruCardButton,
    val secondary: OruCardButton?,
)

/** The patch channel of a source. [preReleaseAvailable] is false when the source offers only stable patches. */
@Immutable
data class OruChannelUi(
    val preRelease: Boolean,
    val supporting: String,
    val preReleaseAvailable: Boolean = true,
)

@Immutable
data class OruNoticeAction(
    val label: String,
    val onClick: () -> Unit,
)

@Immutable
data class OruDialogAction(
    val label: String,
    val onClick: () -> Unit,
)

/** The four looks of the Focus hero. */
enum class OruHeroState { Ready, Working, Done, Failed }

/** One stage of a patch run. [Now] is the running stage, [Error] the stage that stopped the run. */
enum class OruStageState { Todo, Now, Done, Error }

@Immutable
data class OruStageLine(
    val text: String,
    val warning: Boolean = false,
)

/** One stage row. [duration] is already formatted by the caller. */
@Immutable
data class OruStageUi(
    val id: String,
    val name: String,
    val state: OruStageState,
    val duration: String? = null,
    val warnings: Int = 0,
    val lines: List<OruStageLine> = emptyList(),
)

/** Samples for the usage readout, oldest first. [heapLimitMb] scales the heap bars when known. */
@Immutable
data class OruUsageGraphs(
    val heapMb: List<Int>,
    val heapLimitMb: Int?,
    val cpuPercent: List<Int>,
    val ioMbPerSec: List<Int>,
)

/** The trailing part of a settings row. */
sealed interface OruRowControl {
    data class Switch(val checked: Boolean) : OruRowControl
    data class Value(val text: String) : OruRowControl
    object Chevron : OruRowControl
    object None : OruRowControl
}
