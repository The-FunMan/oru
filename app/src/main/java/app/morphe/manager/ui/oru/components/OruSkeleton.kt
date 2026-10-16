/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * A placeholder while content loads: one static rounded rectangle, with no shimmer. The caller sizes it
 * and announces the loading state itself. It is hidden from TalkBack.
 */
@Composable
fun OruSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clearAndSetSemantics { }
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}
