/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import app.morphe.manager.ui.oru.nav.oruSharedTile
import app.morphe.manager.ui.oru.theme.OruBlobShape
import app.morphe.manager.ui.screen.shared.AppIcon

/**
 * The app tile: a blob of [shape] holding the app's real icon when the app is installed, else a neutral
 * letter. [sharedKey] joins the shared bounds of the card and the Focus hero, and is null outside them.
 */
@Composable
fun OruAppTile(
    source: OruTileSource,
    size: Dp,
    shape: OruBlobShape,
    sharedKey: String?,
    modifier: Modifier = Modifier,
) {
    val sized = modifier.size(size)
    val shared = if (sharedKey != null) sized.oruSharedTile(sharedKey) else sized
    // The blob takes the app's accent, and the icon sits inside it at 70 percent of the tile
    val tint = rememberOruAppTint(source.packageInfo)
    Box(
        modifier = shared
            .oruSlab(shape = shape, color = tint.tile, tint = tint.accent, compact = true)
            .semantics { contentDescription = source.name },
        contentAlignment = Alignment.Center,
    ) {
        val packageInfo = source.packageInfo
        if (packageInfo != null) {
            AppIcon(
                packageInfo = packageInfo,
                contentDescription = source.name,
                modifier = Modifier.fillMaxSize(IconShare),
            )
        } else {
            Text(
                text = oruTileLetter(source.name),
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * LetterShare).sp,
                    lineHeight = (size.value * LetterLineShare).sp,
                ),
                color = tint.onContainer,
            )
        }
    }
}

/** Letter size as a share of the tile edge, so the letter scales with the tile. */
private const val LetterShare = 0.42f
private const val LetterLineShare = 0.5f

/** Icon size as a share of the tile edge. */
private const val IconShare = 0.7f
