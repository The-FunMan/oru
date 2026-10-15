/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.manager.ui.oru.nav.oruCtaSharedKey
import app.morphe.manager.ui.oru.nav.oruSharedPill
import app.morphe.manager.ui.oru.nav.oruSharedText
import app.morphe.manager.ui.oru.nav.oruTitleSharedKey

/**
 * One app on Home. The whole card opens the app, and its buttons act on their own. The tap target sits
 * behind the content, so the buttons stay separate nodes for TalkBack.
 *
 * The card is a liquid glass surface (the Card role) tinted with the app's accent. Under a backdrop host it blurs
 * what the host records behind the list; without one, or under the Solid policy, it is the opaque tinted box it
 * has always been. Its buttons and chips take the same tint through [LocalOruAppTint].
 */
@Composable
fun OruAppCard(
    ui: OruCardUi,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPrimary: () -> Unit,
    onSecondary: (() -> Unit)?,
    onSourceClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val secondary = ui.secondary
    val secondaryTap = onSecondary
    // The card takes the app's accent, with a hairline of it at the edge
    val tint = rememberOruAppTint(ui.tile.packageInfo)
    val shape = MaterialTheme.shapes.extraLarge
    // The card presses in while its own tap area is held; a button press does not reach this source. The press squashes
    // the card (OruJelly.Card), its shadow tightens and its light dims
    val interactionSource = remember { MutableInteractionSource() }
    val press = rememberOruPress(interactionSource)
    // The glass casts its own shadow, which a clip here would cut; the tap area is clipped instead
    CompositionLocalProvider(LocalOruAppTint provides tint) {
        OruGlassSurface(
            shape = shape,
            role = OruGlassRole.Card,
            tint = tint.container,
            press = press,
            modifier = modifier
                .oruJellyPress(interactionSource, OruJelly.Card)
                .fillMaxWidth()
                .border(CardOutlineWidth, tint.accent.copy(alpha = CardOutlineAlpha), shape),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .semantics { contentDescription = ui.tile.name }
                    .combinedClickable(
                        interactionSource = interactionSource,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    ),
            )
            Column(
                modifier = Modifier.padding(CardPadding),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    OruAppTile(
                        source = ui.tile,
                        size = TileSize,
                        shape = ui.shape,
                        sharedKey = ui.sharedKey,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ui.tile.name,
                            modifier = Modifier.oruSharedText(oruTitleSharedKey(ui.id)),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = ui.benefit,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (ui.chips.isNotEmpty()) {
                    // Neutral chips take the app's accent from the provided tint
                    OruChipRow(chips = ui.chips)
                }
                // The source the app is patched from, when there is a choice of them. Its own tap opens the choice
                ui.source?.let { source ->
                    OruChip(
                        text = source,
                        tone = OruTone.Neutral,
                        modifier = Modifier.clickable(
                            enabled = onSourceClick != null,
                            onClick = { onSourceClick?.invoke() },
                        ),
                    )
                }
                ui.hint?.let { hint ->
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = HintSize),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Both buttons share the row height, which grows to the taller label at a large font scale
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OruButton(
                        text = ui.primary.label,
                        style = ui.primary.style,
                        onClick = onPrimary,
                        // The pill is the shared element of the Focus dock's primary button, so it travels into the dock
                        modifier = Modifier.weight(1f).fillMaxHeight().oruSharedPill(oruCtaSharedKey(ui.id)),
                        allowTwoLines = true,
                    )
                    if (secondary != null && secondaryTap != null) {
                        OruButton(
                            text = secondary.label,
                            style = secondary.style,
                            onClick = secondaryTap,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            allowTwoLines = true,
                        )
                    }
                }
            }
        }
    }
}

private val CardPadding = 16.dp
private val TileSize = 56.dp
private val HintSize = 12.5.sp

/** The accent hairline around the card. */
private val CardOutlineWidth = 1.dp
private const val CardOutlineAlpha = 0.2f
