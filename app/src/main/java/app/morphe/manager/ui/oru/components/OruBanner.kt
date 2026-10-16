/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.ui.oru.theme.OruSpacing

/**
 * The one banner slot of Home. It shows one message at a time, an optional text action and an optional [leading]
 * mark (a spinner while something runs, a check when it is done).
 */
@Composable
fun OruBanner(
    text: String,
    action: OruNoticeAction?,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .oruSlab(shape = BannerShape, color = MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = BannerPaddingStart, top = BannerPaddingVertical, end = BannerPaddingEnd, bottom = BannerPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leading?.invoke()
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (action != null) {
            OruBannerAction(label = action.label, onClick = action.onClick)
        }
    }
}

/** A text action with a 48 dp touch target. */
@Composable
private fun OruBannerAction(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = OruSpacing.TouchTarget)
            .clip(BannerActionShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = BannerActionPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
}

private val BannerShape = RoundedCornerShape(20.dp)
private val BannerActionShape = RoundedCornerShape(24.dp)
private val BannerPaddingStart = 16.dp
private val BannerPaddingEnd = 8.dp
private val BannerPaddingVertical = 8.dp
private val BannerActionPadding = 12.dp
