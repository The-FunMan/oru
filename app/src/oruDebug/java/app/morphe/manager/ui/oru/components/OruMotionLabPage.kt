/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/*
 * Debug-only page of the motion building blocks: the morphing text, the odometer, the label that grows into a
 * button and the goo pair. Each one is driven by a button here, so its states and its motion can be checked on a
 * device. Labels are English literals on purpose, as in the rest of the gallery.
 */

private val MorphWords = listOf("Preparing", "Patching", "Done")

private const val OdometerTarget = 96
private const val OdometerStepMillis = 180L

/** The motion lab: one section per building block, in one column. */
@Composable
fun OruMotionLabPage() {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        MorphLab()
        OdometerLab()
        LabelLab()
        GooLab()
    }
}

@Composable
private fun MorphLab() {
    var step by remember { mutableStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OruSectionLabel("Morph text")
        OruMorphText(
            text = MorphWords[step % MorphWords.size],
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        OruOutlinedButton(text = "Next word", onClick = { step += 1 })
    }
}

@Composable
private fun OdometerLab() {
    var count by remember { mutableStateOf(0) }
    var counting by remember { mutableStateOf(false) }
    // Counts while running. A stop, or reaching the target, ends the loop; the next start begins again from zero
    LaunchedEffect(counting) {
        while (counting && count < OdometerTarget) {
            delay(OdometerStepMillis)
            count += 1
        }
        counting = false
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OruSectionLabel("Odometer")
        OruOdometer(
            value = count,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        OruPrimaryButton(
            text = if (counting) "Stop" else "Count to $OdometerTarget",
            onClick = {
                if (!counting && count >= OdometerTarget) count = 0
                counting = !counting
            },
        )
    }
}

@Composable
private fun LabelLab() {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OruSectionLabel("Label to button")
        OruLabelToButton(
            expanded = expanded,
            label = "Apply",
            onClick = { expanded = false },
        )
        OruOutlinedButton(text = "Expand or collapse", onClick = { expanded = !expanded })
    }
}

@Composable
private fun GooLab() {
    var split by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OruSectionLabel("Goo pair")
        OruGooPair(
            split = split,
            first = { GooPill("Patch") },
            second = { GooPill("Merge") },
        )
        OruOutlinedButton(text = if (split) "Merge" else "Split", onClick = { split = !split })
    }
}

@Composable
private fun GooPill(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
