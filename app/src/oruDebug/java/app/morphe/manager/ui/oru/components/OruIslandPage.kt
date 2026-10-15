/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.morphe.manager.ui.oru.theme.OruGlassStyle
import app.morphe.manager.ui.oru.theme.LocalOruGlassStyle

/*
 * Debug-only page of the floating island, for the gallery. A list of 40 cards runs under the island. The glass toggle
 * and the accessory toggle change this page only. Labels are English literals on purpose: this page is never shipped.
 */

private val PageHeight = 620.dp

/** The island over a list of 40 cards, in a box of its own height so the gallery column can hold it. */
@Composable
fun OruIslandPage(modifier: Modifier = Modifier) {
    var style by remember { mutableStateOf(OruGlassStyle.Frosted) }
    var accessoryOn by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(OruIslandTab.Apps) }
    var expert by remember { mutableStateOf(false) }
    val islandState = rememberOruIslandState()
    val listState = rememberLazyListState()
    val scheme = MaterialTheme.colorScheme
    val orbColors = listOf(scheme.primary, scheme.tertiary, scheme.secondary)

    val accessory: (@Composable () -> Unit)? = if (accessoryOn) {
        {
            OruRunAccessory(
                appName = "Music",
                progress = 0.42f,
                done = 3,
                total = 7,
                onClick = {},
            )
        }
    } else {
        null
    }
    val accessoryCompact: (@Composable () -> Unit)? = if (accessoryOn) {
        {
            OruRunAccessoryCompact(
                appName = "Music",
                progress = 0.42f,
                onClick = {},
            )
        }
    } else {
        null
    }

    CompositionLocalProvider(LocalOruGlassStyle provides style) {
        Column(
            modifier = modifier.fillMaxWidth().height(PageHeight),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OruGlassToggle(glassStyle = style, onGlassStyleChange = { style = it })
                OruButton(
                    text = if (accessoryOn) "Accessory: on" else "Accessory: off",
                    style = if (accessoryOn) OruButtonStyle.Filled else OruButtonStyle.Outlined,
                    onClick = { accessoryOn = !accessoryOn },
                )
            }
            // The list is a child of this box, so the scroll it reports reaches the island's connection
            val connection = islandState.nestedScroll {
                listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            }
            Box(Modifier.weight(1f).fillMaxWidth().nestedScroll(connection)) {
                OruBackdropHost(
                    modifier = Modifier.fillMaxSize(),
                    backdrop = {
                        Box(Modifier.fillMaxSize()) {
                            OruOrbBackdrop(colors = orbColors, modifier = Modifier.fillMaxSize())
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 12.dp,
                                    top = 8.dp,
                                    end = 12.dp,
                                    bottom = oruIslandClearance(accessoryOn) + 16.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(count = 40) { index -> FakeIslandCard(index) }
                            }
                        }
                    },
                    floating = {
                        OruIsland(
                            state = islandState,
                            expert = expert,
                            onModeChange = { expert = it },
                            selected = selected,
                            onTabClick = { selected = it },
                            modifier = Modifier.align(Alignment.BottomCenter),
                            accessory = accessory,
                            accessoryCompact = accessoryCompact,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun FakeIslandCard(index: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
            .padding(16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "App ${index + 1}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
