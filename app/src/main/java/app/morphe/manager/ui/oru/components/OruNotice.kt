/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.manager.ui.oru.theme.OruPillShape
import app.morphe.manager.ui.oru.theme.OruSpacing

/**
 * A notice in the tone's container: an icon, the text, and up to two outlined actions. The icon is
 * decorative, the text carries the meaning.
 */
@Composable
fun OruNotice(
    text: String,
    tone: OruTone,
    actions: List<OruNoticeAction>,
    modifier: Modifier = Modifier,
) {
    val colors = oruContainerColors(oruContainerRole(tone))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .oruSlab(shape = NoticeShape, color = colors.container)
            .padding(horizontal = NoticePaddingHorizontal, vertical = NoticePaddingVertical),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // The icon is centred on the first text line, not on the top of the row
        val firstLineHeight = with(LocalDensity.current) { NoticeLineHeight.toDp() }
        Box(
            modifier = Modifier.height(firstLineHeight),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = oruToneIcon(tone),
                contentDescription = null,
                tint = colors.content,
                modifier = Modifier.size(NoticeIconSize),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = NoticeTextSize, lineHeight = NoticeLineHeight),
                color = colors.content,
            )
            if (actions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.forEach { action ->
                        OruOutlinedTextAction(
                            label = action.label,
                            color = colors.content,
                            onClick = action.onClick,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            }
        }
    }
}

/** An outlined pill action with a 48 dp touch target. Its label wraps rather than clips. */
@Composable
internal fun OruOutlinedTextAction(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = OruSpacing.TouchTarget)
            .clip(OruPillShape)
            .border(width = OutlineWidth, color = color, shape = OruPillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ActionPaddingHorizontal, vertical = ActionPaddingVertical),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = ActionTextSize, fontWeight = FontWeight.SemiBold),
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}

private fun oruToneIcon(tone: OruTone): ImageVector = when (tone) {
    OruTone.Neutral -> Icons.Outlined.Info
    OruTone.Ok -> Icons.Outlined.Check
    OruTone.Warn -> Icons.Outlined.Warning
    OruTone.Error -> Icons.Outlined.ErrorOutline
}

private val NoticeShape = RoundedCornerShape(20.dp)
private val NoticePaddingHorizontal = 14.dp
private val NoticePaddingVertical = 12.dp
private val NoticeIconSize = 22.dp
private val NoticeTextSize = 13.5.sp
private val NoticeLineHeight = 19.sp
private val ActionPaddingHorizontal = 14.dp
private val ActionPaddingVertical = 12.dp
private val ActionTextSize = 13.sp
private val OutlineWidth = 1.dp
