/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.morphe.manager.ui.oru.theme.OruTheme

/**
 * Debug-only gallery of the Oru components. Start it with
 * `adb shell am start -n app.oru.manager.debug/app.morphe.manager.ui.oru.components.OruGalleryActivity`.
 * The toggles set light or dark, font scale 1.3, a 320 dp width and the reduced-motion preview.
 */
class OruGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OruGalleryHost()
        }
    }
}

@Composable
private fun OruGalleryHost() {
    var darkTheme by remember { mutableStateOf(false) }
    var largeFont by remember { mutableStateOf(false) }
    var reducedMotion by remember { mutableStateOf(false) }
    var narrow by remember { mutableStateOf(false) }

    val baseConfiguration = LocalConfiguration.current
    val baseDensity = LocalDensity.current
    // Light or dark is forced by the night bit of the configuration, which the Oru theme reads
    val configuration = remember(baseConfiguration, darkTheme) {
        Configuration(baseConfiguration).apply {
            val nightMode = if (darkTheme) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }
    }
    val density = Density(
        density = baseDensity.density,
        fontScale = if (largeFont) LargeFontScale else baseDensity.fontScale,
    )

    CompositionLocalProvider(
        LocalConfiguration provides configuration,
        LocalDensity provides density,
        LocalOruReducedMotionPreview provides reducedMotion,
    ) {
        OruTheme {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GalleryToggle(
                        label = "Dark",
                        on = darkTheme,
                        modifier = Modifier.weight(1f),
                        onChange = { darkTheme = it },
                    )
                    GalleryToggle(
                        label = "Font 1.3",
                        on = largeFont,
                        modifier = Modifier.weight(1f),
                        onChange = { largeFont = it },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GalleryToggle(
                        label = "Reduced motion",
                        on = reducedMotion,
                        modifier = Modifier.weight(1f),
                        onChange = { reducedMotion = it },
                    )
                    GalleryToggle(
                        label = "320 dp",
                        on = narrow,
                        modifier = Modifier.weight(1f),
                        onChange = { narrow = it },
                    )
                }
                OruGallery(
                    modifier = Modifier
                        .weight(1f)
                        .then(if (narrow) Modifier.width(NarrowWidth) else Modifier.fillMaxWidth()),
                )
            }
        }
    }
}

@Composable
private fun GalleryToggle(
    label: String,
    on: Boolean,
    modifier: Modifier,
    onChange: (Boolean) -> Unit,
) {
    OruButton(
        text = "$label: ${if (on) "on" else "off"}",
        style = if (on) OruButtonStyle.Filled else OruButtonStyle.Outlined,
        onClick = { onChange(!on) },
        modifier = modifier,
    )
}

private const val LargeFontScale = 1.3f
private val NarrowWidth = 320.dp
