/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing on a 4 dp grid. Screens take their gaps from here rather than writing literals. */
object OruSpacing {
    val Grid: Dp = 4.dp
    val Small: Dp = 8.dp
    val Medium: Dp = 12.dp
    val Large: Dp = 16.dp
    val XLarge: Dp = 24.dp

    /** Horizontal gutter of every screen, matching the prototype's `.scroll` padding. */
    val ScreenGutter: Dp = 20.dp

    /** Smallest touch target. The prototype's 40 px mode toggle becomes this size. */
    val TouchTarget: Dp = 48.dp

    /** Gap between the rows of one group. The group's background shows through it. */
    val GroupGap: Dp = 2.dp
}
