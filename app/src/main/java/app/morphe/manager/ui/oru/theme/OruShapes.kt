/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The Material 3 corner scale, 8 to 28 dp. Pill buttons (48 dp high, 24 dp radius) are [PillShape];
 * the app tile is a blob (see [OruBlobShape]) and is not part of this scale.
 */
val OruShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Buttons: 48 dp high with a 24 dp radius. */
val OruPillShape = RoundedCornerShape(24.dp)

/** Floating dock and the top edge of sheets. */
val OruDockShape = RoundedCornerShape(28.dp)

/** A group of rows (Focus Expert rows) is one container with this outer radius. */
val OruGroupShape = RoundedCornerShape(24.dp)

/** The corners between two rows of a group, and every corner of a row outside one. */
val OruGroupInnerShape = RoundedCornerShape(4.dp)
