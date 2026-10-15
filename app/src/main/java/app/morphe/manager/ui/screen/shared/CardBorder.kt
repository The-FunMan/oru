/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.ui.theme.ThemeTraitsDefaults

/**
 * Hairlines for the outermost card of a group. Rows inside it take none, a panel on it, such as an
 * [InfoPanel], takes one in the card's color.
 *
 * Every decorative edge, a card's or a button's, comes from [of], so the outlines setting takes
 * them all off at once. An edge that is the only mark of a state, such as a picked chip, is drawn
 * without it.
 */
object CardBorder {
    /** Shared by both, so a tinted card and a neutral one line up side by side. */
    private val Width: Dp = 1.dp

    /** Low enough that the accent traces the card without competing with its own fill. */
    private const val TINTED_ALPHA = 0.25f

    /** Edge in [color], or none where the appearance settings take outlines off. */
    @Composable
    fun of(color: Color, width: Dp = Width): BorderStroke? = ThemeTraitsDefaults.outline(color, width)

    /** Edge for a card whose fill alone cannot hold it apart from a black background. */
    val neutral: BorderStroke?
        @Composable get() = of(GlassButtonDefaults.borderColor())

    /** Edge drawn in the same role as a tinted card's fill, so the tint carries to its outline. */
    @Composable
    fun tinted(accent: Color): BorderStroke? = of(accent.copy(alpha = TINTED_ALPHA))
}

/** Draws [border] around [shape], or nothing where [CardBorder] leaves the edge off. */
fun Modifier.cardBorder(border: BorderStroke?, shape: Shape): Modifier =
    if (border != null) border(border, shape) else this
