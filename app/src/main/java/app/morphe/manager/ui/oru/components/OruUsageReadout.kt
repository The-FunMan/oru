/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.manager.R
import app.morphe.manager.ui.oru.theme.OruMonoStyle
import app.morphe.manager.ui.oru.theme.OruMotion

/**
 * Units of the three readouts. The defaults are English literals because the keys for them are not in
 * oru_strings.xml yet: oru_vital_unit_mb, oru_vital_unit_percent and oru_vital_unit_mb_s.
 */
data class OruVitalUnits(
    val heap: String = "MB",
    val cpu: String = "%",
    val io: String = "MB/s",
)

/**
 * The one-line usage readout of an expert run: heap, CPU and I/O as figures in tabular numerals. The row
 * opens the mini bar graphs when [graphs] is set. TalkBack reads the figures, not the bars, which are
 * hidden from it. A missing figure reads as a dash.
 */
@Composable
fun OruUsageReadout(
    heapMb: Int?,
    cpuPercent: Int?,
    ioMbPerSec: Int?,
    graphs: OruUsageGraphs?,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    units: OruVitalUnits = OruVitalUnits(),
) {
    val reduced = oruReducedMotion()
    val scheme = MaterialTheme.colorScheme
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) ExpandedAngle else CollapsedAngle,
        animationSpec = OruMotion.expand(reduced),
        label = "oruUsageChevron",
    )
    val heapLabel = stringResource(R.string.oru_vital_heap)
    val cpuLabel = stringResource(R.string.oru_vital_cpu)
    val ioLabel = stringResource(R.string.oru_vital_io)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = ReadoutMinHeight)
                .oruSlab(shape = ReadoutShape, color = scheme.surfaceContainer)
                .then(if (graphs != null) Modifier.clickable(role = Role.Button, onClick = onToggle) else Modifier)
                .padding(start = ReadoutPaddingStart, top = ReadoutPaddingVertical, end = ReadoutPaddingEnd, bottom = ReadoutPaddingVertical),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OruVital(label = heapLabel, value = oruFigure(heapMb), unit = units.heap, modifier = Modifier.weight(1f))
            OruVital(label = cpuLabel, value = oruFigure(cpuPercent), unit = units.cpu, modifier = Modifier.weight(1f))
            OruVital(label = ioLabel, value = oruFigure(ioMbPerSec), unit = units.io, modifier = Modifier.weight(1f))
            if (graphs != null) {
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(ChevronSize)
                        .graphicsLayer { rotationZ = chevronAngle },
                )
            }
        }
        if (graphs != null) {
            AnimatedVisibility(
                visible = expanded,
                enter = if (reduced) {
                    EnterTransition.None
                } else {
                    fadeIn(OruMotion.fade(reduced)) + expandVertically(OruMotion.expand(reduced))
                },
                exit = if (reduced) {
                    ExitTransition.None
                } else {
                    fadeOut(OruMotion.fade(reduced)) + shrinkVertically(OruMotion.expand(reduced))
                },
            ) {
                OruUsageGraphsPanel(
                    graphs = graphs,
                    heapLabel = heapLabel,
                    cpuLabel = cpuLabel,
                    ioLabel = ioLabel,
                )
            }
        }
    }
}

/** One readout figure: a small label over the value and its unit. */
@Composable
private fun OruVital(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // The figure stays on one line and shrinks to fit before the unit is pushed out of the pill
            OruFitText(
                text = value,
                style = OruMonoStyle.copy(fontSize = FigureSize, lineHeight = FigureLine, fontWeight = FontWeight.SemiBold),
                color = scheme.onSurface,
            )
            Text(
                text = unit,
                style = OruMonoStyle.copy(fontSize = UnitSize, lineHeight = UnitLine),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** The three mini bar graphs: heap, CPU and I/O, oldest sample on the left. */
@Composable
private fun OruUsageGraphsPanel(
    graphs: OruUsageGraphs,
    heapLabel: String,
    cpuLabel: String,
    ioLabel: String,
) {
    val limit = graphs.heapLimitMb
    val heapCaption = if (limit != null) {
        "$heapLabel, ${stringResource(R.string.oru_vital_heap_limit, limit)}"
    } else {
        heapLabel
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = GraphsTopPadding, bottom = GraphsBottomPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OruBarGraph(
            caption = heapCaption,
            samples = graphs.heapMb,
            scale = oruGraphScale(limit, graphs.heapMb),
            hot = true,
            modifier = Modifier.weight(1f),
        )
        OruBarGraph(
            caption = cpuLabel,
            samples = graphs.cpuPercent,
            scale = CpuScale,
            hot = false,
            modifier = Modifier.weight(1f),
        )
        OruBarGraph(
            caption = ioLabel,
            samples = graphs.ioMbPerSec,
            scale = oruGraphScale(null, graphs.ioMbPerSec),
            hot = false,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A mini bar graph. The bars are decorative, so TalkBack skips them and reads the figures above. */
@Composable
private fun OruBarGraph(
    caption: String,
    samples: List<Int>,
    scale: Int,
    hot: Boolean,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(GraphShape)
            .background(scheme.surfaceContainer)
            .padding(horizontal = GraphPadding, vertical = GraphPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BarAreaHeight)
                .clearAndSetSemantics { },
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            samples.forEach { value ->
                val fraction = oruBarFraction(value, scale)
                val barColor = if (hot && oruBarIsHot(fraction)) scheme.error else scheme.primary.copy(alpha = BarAlpha)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((BarAreaHeight * fraction).coerceAtLeast(MinBarHeight))
                        .clip(BarShape)
                        .background(barColor),
                )
            }
        }
    }
}

private val ReadoutShape = RoundedCornerShape(24.dp)
private val ReadoutMinHeight = 48.dp
private val ReadoutPaddingStart = 16.dp
private val ReadoutPaddingEnd = 10.dp
private val ReadoutPaddingVertical = 8.dp
private val ChevronSize = 22.dp
private val FigureSize = 15.sp
private val FigureLine = 20.sp
private val UnitSize = 11.sp
private val UnitLine = 14.sp
private val GraphsTopPadding = 8.dp
private val GraphsBottomPadding = 10.dp
private val GraphShape = RoundedCornerShape(18.dp)
private val GraphPadding = 10.dp
private val BarAreaHeight: Dp = 34.dp
private val MinBarHeight: Dp = 4.dp
private val BarShape = RoundedCornerShape(2.dp)
private const val BarAlpha = 0.85f
private const val CpuScale = 100
private const val CollapsedAngle = 0f
private const val ExpandedAngle = 180f
