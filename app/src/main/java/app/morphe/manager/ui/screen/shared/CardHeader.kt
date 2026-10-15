/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Heading band across the top of a card: an [icon] or [leading] mark, the [title] with an optional
 * [description], and [trailing] badges at the far edge.
 *
 * @param accentColor The card's edge color, which the band takes at [AccentAlpha.CARD]. Null for
 *   the neutral veil.
 */
@Composable
fun CardHeader(
    title: String?,
    modifier: Modifier = Modifier,
    description: String? = null,
    accentColor: Color? = LocalAccent.current,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(accentColor?.copy(alpha = AccentAlpha.CARD) ?: neutralVeil())
            .padding(horizontal = Defaults.ContentPadding, vertical = Defaults.ContentPaddingSmall),
        horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            leading != null -> leading()
            icon != null -> Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
        if (title != null) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        trailing?.invoke(this)
    }
}
