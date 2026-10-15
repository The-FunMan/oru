/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.morphe.manager.ui.oru.run.oruRunFill
import app.morphe.manager.ui.oru.theme.OruBlobShape
import app.morphe.manager.ui.oru.theme.OruSurfaceFill

/*
 * Debug-only gallery for the Oru components, every component in every state. Opened with
 * OruGalleryActivity. Labels are English literals on purpose: this screen is for the lead's visual checks
 * and is never shipped. The toggles in the activity set light and dark, font scale 1.3, a 320 dp width and
 * the reduced-motion preview.
 */

/** Every component in every state, in one scrolling column. */
@Composable
fun OruGallery(modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    var expert by remember { mutableStateOf(false) }
    var expandedStage by remember { mutableStateOf<String?>(null) }
    var usageOpen by remember { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(false) }
    var dialogOpen by remember { mutableStateOf(false) }
    var warnDialogOpen by remember { mutableStateOf(false) }
    var preRelease by remember { mutableStateOf(false) }
    var autoUpdates by remember { mutableStateOf(true) }
    var wallpaper by remember { mutableStateOf(false) }

    // Demo run: drives the hero, the ring and the percentage. Start it with the button below.
    var demoRunning by remember { mutableStateOf(false) }
    val demo = remember { Animatable(0f) }
    LaunchedEffect(demoRunning) {
        if (demoRunning) {
            demo.snapTo(0f)
            demo.animateTo(1f, animationSpec = tween(durationMillis = DemoMillis))
            demoRunning = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(GalleryPadding),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        GallerySection("Wordmark and app tiles") {
            OruWordmark()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OruBlobShape.Presets.forEach { preset ->
                    OruAppTile(source = OruTileSource("Video player"), size = 56.dp, shape = preset, sharedKey = null)
                }
                OruAppTile(source = OruTileSource("Music"), size = 84.dp, shape = OruBlobShape.Second, sharedKey = null)
            }
        }

        GallerySection("App cards") {
            OruAppCard(
                ui = sampleCard(
                    id = "video",
                    name = "Video player",
                    source = "Default patches",
                    benefit = "Removes ads and plays in the background",
                    chips = listOf(OruChipUi("Ready", OruTone.Ok), OruChipUi("21.40.161", mono = true)),
                    hint = null,
                    primary = OruCardButton("Upgrade", OruButtonStyle.Filled),
                    secondary = null,
                ),
                onClick = {},
                onLongClick = {},
                onPrimary = {},
                onSecondary = null,
            )
            OruAppCard(
                ui = sampleCard(
                    id = "music",
                    name = "Music",
                    benefit = "Removes ads and plays with the screen off",
                    chips = listOf(OruChipUi("APK needed", OruTone.Warn)),
                    hint = "APKMirror opens in your browser for version 8.33.51. Download it, then come back and choose the file.",
                    primary = OruCardButton("Find on APKMirror", OruButtonStyle.Tonal),
                    secondary = OruCardButton("Use a file", OruButtonStyle.Outlined),
                ),
                onClick = {},
                onLongClick = {},
                onPrimary = {},
                onSecondary = {},
            )
            OruAppCard(
                ui = sampleCard(
                    id = "reader",
                    name = "Reader",
                    benefit = "Hides promoted posts and tracking, with a long benefit line that wraps at 1.3 scale",
                    chips = listOf(
                        OruChipUi("Waiting for the APK", OruTone.Warn),
                        OruChipUi("2024.12.0", mono = true),
                    ),
                    hint = null,
                    primary = OruCardButton("Choose the file", OruButtonStyle.Filled),
                    secondary = OruCardButton("Open APKMirror", OruButtonStyle.Outlined),
                ),
                onClick = {},
                onLongClick = {},
                onPrimary = {},
                onSecondary = {},
            )
        }

        GallerySection("Mode toggle") {
            OruModeToggle(expert = expert, onChange = { expert = it })
        }

        GallerySection("Buttons") {
            OruPrimaryButton(text = "Upgrade", onClick = {})
            OruPrimaryButton(text = "Download", onClick = {}, icon = Icons.Outlined.Download)
            OruPrimaryButton(text = "Disabled", onClick = {}, enabled = false)
            OruSecondaryButton(text = "Use a file", onClick = {})
            OruOutlinedButton(text = "Open APKMirror", onClick = {})
        }

        GallerySection("Chips") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OruChip(text = "Neutral", tone = OruTone.Neutral)
                OruChip(text = "Ok", tone = OruTone.Ok)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OruChip(text = "Warn", tone = OruTone.Warn)
                OruChip(text = "Error", tone = OruTone.Error)
            }
            OruChip(text = "8.33.51, monospace", tone = OruTone.Neutral, mono = true)
        }

        GallerySection("Notices and banner") {
            OruNotice(
                text = "Version 21.41.0 is newer than the patches were tested with, so it may not work.",
                tone = OruTone.Warn,
                actions = listOf(OruNoticeAction("Pick another version", onClick = {})),
            )
            OruNotice(
                text = "Installed with another signing key. Updating it erases its data.",
                tone = OruTone.Error,
                actions = listOf(
                    OruNoticeAction("Import key", onClick = {}),
                    OruNoticeAction("Back up now", onClick = {}),
                ),
            )
            OruNotice(text = "Signed on this phone.", tone = OruTone.Ok, actions = emptyList())
            OruNotice(text = "Oru can patch this app.", tone = OruTone.Neutral, actions = emptyList())
            OruBanner(text = "Some patch sources need attention.", action = OruNoticeAction("Review", onClick = {}))
            OruBanner(text = "You are on mobile data. Updates can wait for Wi-Fi.", action = null)
        }

        GallerySection("Rows and channel") {
            OruRow(title = "Version", value = "21.40.161, recommended", onClick = {})
            OruRow(title = "Patches", value = "96 of 96 selected", onClick = {})
            OruRow(title = "Install with", value = null, onClick = null, trailing = { Text("Shizuku") })
            OruChannelRow(
                channel = OruChannelUi(preRelease = false, supporting = "From the patch source, stable"),
                onSelect = { preRelease = it },
            )
            OruChannelRow(
                channel = OruChannelUi(preRelease = preRelease, supporting = "Pre-release, 2026.10.12-dev.3"),
                onSelect = { preRelease = it },
            )
            OruChannelRow(
                channel = OruChannelUi(preRelease = false, supporting = "This source only offers stable patches.", preReleaseAvailable = false),
                onSelect = {},
            )
        }

        GallerySection("Settings rows") {
            OruSettingsRow(
                title = "Keep apps up to date",
                supporting = "Tells you when a patched app has an update",
                control = OruRowControl.Switch(checked = autoUpdates),
                onClick = { autoUpdates = !autoUpdates },
            )
            OruSettingsRow(
                title = "Match my wallpaper colors",
                supporting = "Needs Android 12 or later",
                control = OruRowControl.Switch(checked = wallpaper),
                onClick = { wallpaper = !wallpaper },
            )
            OruSettingsRow(
                title = "Free up space",
                supporting = null,
                control = OruRowControl.Value("1.2 GB can be removed"),
                onClick = {},
            )
            OruSettingsRow(title = "Language", supporting = "Phone language", control = OruRowControl.Chevron, onClick = {})
            OruSettingsRow(title = "Signing key", supporting = null, control = OruRowControl.None, onClick = {})
        }

        GallerySection("Hero") {
            val tile = OruTileSource("Video player")
            val shape = OruBlobShape.First
            HeroCaption("Ready")
            OruHero(state = OruHeroState.Ready, tile = tile, shape = shape, progress = null, compact = false)
            HeroCaption("Working, preparing hexagon (tap the demo button to run the demo)")
            OruHero(
                state = OruHeroState.Working,
                tile = tile,
                shape = shape,
                progress = { demo.value },
                compact = false,
                description = "Applying patches",
            )
            OruButton(
                text = if (demoRunning) "Running" else "Run demo",
                style = OruButtonStyle.Tonal,
                onClick = { demoRunning = true },
            )
            HeroCaption("Working, hexagon of 24 pieces")
            OruHero(
                state = OruHeroState.Working,
                tile = tile,
                shape = shape,
                progress = { demo.value },
                compact = false,
                applyingPatches = true,
                patchTotal = 24,
                fill = oruRunFill(demo.value, installAhead = true),
                patchShare = oruRunFill(GalleryPatchShare, installAhead = true),
            )
            HeroCaption("Working, expert hexagon of 96 patches")
            OruHero(
                state = OruHeroState.Working,
                tile = tile,
                shape = shape,
                progress = { demo.value },
                compact = true,
                applyingPatches = true,
                patchTotal = 96,
                fill = oruRunFill(demo.value, installAhead = true),
                patchShare = oruRunFill(GalleryPatchShare, installAhead = true),
            )
            HeroCaption("Done")
            OruHero(state = OruHeroState.Done, tile = tile, shape = shape, progress = null, compact = false)
            HeroCaption("Failed")
            OruHero(state = OruHeroState.Failed, tile = tile, shape = shape, progress = null, compact = false)
        }

        GallerySection("Hexagon") {
            // Each state of the run's hexagon: the fill, the patch count and whether the run failed
            val accent = MaterialTheme.colorScheme.primary
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HexagonSample("Waiting", accent, fill = 0f, patches = 0, failed = false, preparing = true)
                HexagonSample("Mid fill", accent, fill = 0.36f, patches = 96)
                HexagonSample("Nearly full", accent, fill = oruRunFill(1f, installAhead = true), patches = 96)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HexagonSample("Settled", accent, fill = 1f, patches = 96)
                HexagonSample("Failed", accent, fill = 0.5f, patches = 96, failed = true)
            }
        }

        GallerySection("Stage rail") {
            OruStageRail(
                stages = sampleStages(failed = false),
                expandedId = expandedStage,
                onToggle = { id -> expandedStage = if (expandedStage == id) null else id },
            )
            HeroCaption("Failed run")
            OruStageRail(stages = sampleStages(failed = true), expandedId = null, onToggle = {})
        }

        GallerySection("Usage readout") {
            OruUsageReadout(
                heapMb = 512,
                cpuPercent = 40,
                ioMbPerSec = 12,
                graphs = sampleGraphs(),
                expanded = usageOpen,
                onToggle = { usageOpen = !usageOpen },
            )
            OruUsageReadout(
                heapMb = null,
                cpuPercent = null,
                ioMbPerSec = null,
                graphs = null,
                expanded = false,
                onToggle = {},
            )
        }

        GallerySection("Frosted dock") {
            OruFrostedDock(frost = OruSurfaceFill.Frosted) {
                OruPrimaryButton(text = "Patch Video player", onClick = {}, modifier = Modifier.weight(1f))
            }
            OruFrostedDock(frost = OruSurfaceFill.Solid) {
                OruPrimaryButton(text = "Patch Video player", onClick = {}, modifier = Modifier.weight(1f))
            }
        }

        GallerySection("Frosted sheet") {
            OruButton(
                text = if (sheetOpen) "Close sheet" else "Open sheet",
                style = OruButtonStyle.Tonal,
                onClick = { sheetOpen = !sheetOpen },
            )
            Box(modifier = Modifier.fillMaxWidth().height(SheetPreviewHeight)) {
                OruFrostedSheet(
                    visible = sheetOpen,
                    onDismiss = { sheetOpen = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text("Settings", style = MaterialTheme.typography.titleLarge)
                        OruSettingsRow(
                            title = "Keep apps up to date",
                            supporting = null,
                            control = OruRowControl.Switch(checked = autoUpdates),
                            onClick = { autoUpdates = !autoUpdates },
                        )
                    }
                }
            }
        }

        GallerySection("Decision dialogs") {
            OruButton(text = "Open signature dialog", style = OruButtonStyle.Outlined, onClick = { dialogOpen = true })
            OruButton(text = "Open warning dialog", style = OruButtonStyle.Outlined, onClick = { warnDialogOpen = true })
        }
        if (dialogOpen) {
            OruDecisionDialog(
                title = "Different signing key",
                body = "Video player is installed with another signing key, so Android cannot update it in place.",
                primary = OruDialogAction("Remove it and install", onClick = { dialogOpen = false }),
                secondary = OruDialogAction("Cancel", onClick = { dialogOpen = false }),
                tone = OruTone.Error,
                hint = "Have the old key? Import it in Settings and Oru can update in place.",
                onDismiss = { dialogOpen = false },
            )
        }
        if (warnDialogOpen) {
            OruDecisionDialog(
                title = "Patch an unsupported version?",
                body = "The patches were not tested with this version.",
                primary = OruDialogAction("Patch anyway", onClick = { warnDialogOpen = false }),
                secondary = OruDialogAction("Not now", onClick = { warnDialogOpen = false }),
                tone = OruTone.Warn,
                onDismiss = { warnDialogOpen = false },
            )
        }

        GallerySection("Section label and skeleton") {
            OruSectionLabel("Expert")
            OruSkeleton(modifier = Modifier.fillMaxWidth().height(SkeletonHeight))
            OruSkeleton(modifier = Modifier.fillMaxWidth().height(SkeletonHeightShort))
        }

        OruMotionLabPage()
        GallerySection("Floating island") { OruIslandPage() }
    }
}

@Composable
private fun GallerySection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OruSectionLabel(title)
        content()
    }
}

/** The patch stage's share of a whole patch run, as the gallery's samples cut the hexagon for it. */
private const val GalleryPatchShare = 0.4f

/** One hexagon sample of the gallery, with its state named above it. */
@Composable
private fun HexagonSample(
    name: String,
    accent: Color,
    fill: Float,
    patches: Int,
    patchShare: Float = oruRunFill(GalleryPatchShare, installAhead = true),
    failed: Boolean = false,
    preparing: Boolean = false,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HeroCaption(name)
        OruHexagonAssembly(
            fill = fill,
            patches = patches,
            patchShare = patchShare,
            failed = failed,
            accent = accent,
            modifier = Modifier.size(96.dp),
            preparing = preparing,
        )
    }
}

@Composable
private fun HeroCaption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun sampleCard(
    id: String,
    name: String,
    benefit: String,
    chips: List<OruChipUi>,
    hint: String?,
    primary: OruCardButton,
    secondary: OruCardButton?,
    source: String? = null,
) = OruCardUi(
    id = id,
    tile = OruTileSource(name),
    shape = OruBlobShape.presetFor(id),
    sharedKey = null,
    benefit = benefit,
    chips = chips,
    source = source,
    hint = hint,
    primary = primary,
    secondary = secondary,
)

private fun sampleStages(failed: Boolean): List<OruStageUi> {
    val states = oruStageStates(count = 5, currentIndex = 2, failed = failed)
    val names = listOf(
        "Loading patches",
        "Read APK file",
        "Applying patches",
        "Writing patched APK file",
        "Signing patched APK file",
    )
    val lines = listOf(
        listOf(OruStageLine("Loaded 2 patch sources")),
        listOf(OruStageLine("5173 binary XML files"), OruStageLine("Resource table has 2.4 million entries")),
        listOf(
            OruStageLine("Patch 1 of 96, Remove ads"),
            OruStageLine("Fingerprint fell back to a full scan", warning = true),
            OruStageLine("Patch 12 of 96, Background playback"),
            OruStageLine("Patch 18 of 96, Hide shorts shelf"),
        ),
        emptyList(),
        emptyList(),
    )
    return names.indices.map { index ->
        OruStageUi(
            id = "stage$index",
            name = names[index],
            state = states[index],
            duration = if (states[index] == OruStageState.Done) "${index + 1}.0 s" else null,
            warnings = if (index == 2) 1 else 0,
            lines = lines[index],
        )
    }
}

private fun sampleGraphs() = OruUsageGraphs(
    heapMb = listOf(180, 220, 260, 300, 340, 380, 420, 470, 500, 512, 480, 512, 490, 512),
    heapLimitMb = 1024,
    cpuPercent = listOf(35, 52, 61, 48, 70, 88, 64, 40),
    ioMbPerSec = listOf(8, 14, 40, 66, 12, 9, 30, 55, 20, 11, 18, 25, 10, 14),
)

private const val DemoMillis = 6000
private val GalleryPadding = 16.dp
private val SheetPreviewHeight = 360.dp
private val SkeletonHeight = 88.dp
private val SkeletonHeightShort = 24.dp
