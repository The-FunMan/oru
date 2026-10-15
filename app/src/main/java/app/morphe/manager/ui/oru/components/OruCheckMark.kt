/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.min

/**
 * The check of a settled patch visual, drawn over the centre ([cx], [cy]) at size [s]. [progress] from 0 to 1 draws
 * it in: the short stroke first, then the long one. Reduced motion passes 1 and the check is simply there.
 */
internal fun DrawScope.drawOruCheck(
    cx: Float,
    cy: Float,
    s: Float,
    color: Color,
    alpha: Float,
    progress: Float,
) {
    if (progress <= 0f) return
    val p = progress.coerceIn(0f, 1f)
    val start = Offset(cx - s * 0.36f, cy + s * 0.02f)
    val corner = Offset(cx - s * 0.1f, cy + s * 0.28f)
    val end = Offset(cx + s * 0.38f, cy - s * 0.26f)
    val strokeWidth = s * 0.16f
    val shortShare = min(1f, p * 2f)
    val longShare = (p * 2f - 1f).coerceAtLeast(0f)
    drawLine(
        color = color,
        start = start,
        end = Offset(
            start.x + (corner.x - start.x) * shortShare,
            start.y + (corner.y - start.y) * shortShare,
        ),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
        alpha = alpha,
    )
    if (longShare > 0f) {
        drawLine(
            color = color,
            start = corner,
            end = Offset(
                corner.x + (end.x - corner.x) * longShare,
                corner.y + (end.y - corner.y) * longShare,
            ),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
            alpha = alpha,
        )
    }
}
