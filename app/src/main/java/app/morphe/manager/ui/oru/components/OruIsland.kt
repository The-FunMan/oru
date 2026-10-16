/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.oru.theme.OruMotion
import app.morphe.manager.ui.oru.theme.oruSpring
import app.morphe.manager.ui.oru.theme.OruSpacing
import app.morphe.manager.ui.oru.theme.LocalOruLight
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The floating island. A glass capsule stands over the bottom of Home with its sections in it: Apps, the Simple and
 * Expert switch, and Settings. When the list scrolls down it shrinks to a capsule of icons, and it opens again when
 * the list scrolls up or the island is tapped. An accessory card (a run in progress, say) sits 8 dp above it; when
 * the island shrinks, the accessory shrinks with it and slides inline to its left.
 *
 * The surface is a OruGlassSurface, so the glass, the rim and the shadow are the ones of the rest of the app. The lens
 * behind the selected section is a slab, and a glint on the lit side follows the tilt light. The morph is one Layout
 * measured from a single number, 0 expanded to 1 collapsed, so it costs a layout per frame and no recomposition, except
 * where the expanded and the collapsed contents switch (at the halfway point). Reduced motion cross-fades the morph and
 * slides the lens on a short tween, and the lens overshoots only with motion on.
 */

/** The sections of the island. The Simple and Expert switch in the middle is a control, not a section. */
enum class OruIslandTab { Apps, Settings }

/** The island after a scroll step: whether it is collapsed, and the downward distance counted so far, in dp. */
data class OruIslandStep(val collapsed: Boolean, val accumulated: Float)

/** The downward scroll, in dp, after which the island collapses. */
private const val CollapseDistanceDp = 24f

/**
 * The next state of the island after a scroll step. [delta] is the scroll the list consumed, in dp: negative when the
 * finger moves up and the list shows more below (a downward scroll), positive when it moves down. [accumulated] is the
 * downward distance counted since the last upward scroll.
 *
 * - At the top of the list the island is open and the count is cleared.
 * - Any upward scroll opens the island and clears the count, at once.
 * - A downward scroll counts its distance; the island collapses once the count reaches [CollapseDistanceDp], and
 *   stays collapsed while the list keeps going down. The count is held at the threshold once it is reached.
 *
 * Because a collapse needs a whole run of downward scroll and an upward scroll resets the run, a list that wobbles
 * around one place does not flicker the island.
 */
internal fun oruIslandCollapsed(
    current: Boolean,
    delta: Float,
    accumulated: Float,
    atTop: Boolean,
): OruIslandStep {
    if (atTop || delta > 0f) return OruIslandStep(collapsed = false, accumulated = 0f)
    if (delta == 0f) return OruIslandStep(collapsed = current, accumulated = accumulated)
    val down = accumulated - delta
    val collapsed = current || down >= CollapseDistanceDp
    return OruIslandStep(collapsed = collapsed, accumulated = if (collapsed) CollapseDistanceDp else down)
}

/** The width of an Apps or Settings tab, in dp, with its label [labelDp] wide: never less than [IslandTabMinDp]. */
internal fun oruIslandTabDp(labelDp: Float): Float = max(IslandTabMinDp, IslandTabChromeDp + labelDp)

/**
 * Whether the Apps and Settings tabs keep their labels in the expanded island. They keep them when both tabs and the
 * Simple and Expert switch fit side by side in [availableDp]. Otherwise the tabs show their icons only, so no label is
 * cut short. The widths are the ones they take at the current font scale, in dp.
 */
internal fun oruIslandShowLabels(
    availableDp: Float,
    appsLabelDp: Float,
    settingsLabelDp: Float,
    switchDp: Float,
): Boolean = oruIslandTabDp(appsLabelDp) + oruIslandTabDp(settingsLabelDp) + switchDp <= availableDp

/**
 * Whether the island is collapsed, and the scroll it has counted. [rememberOruIslandState] makes one per screen.
 * [nestedScroll] is the connection the list reports its scroll to; [expand] opens the island again, as a tap does.
 */
@Stable
class OruIslandState internal constructor(private val density: Float) {
    /** True while the island is in its small form. */
    var collapsed: Boolean by mutableStateOf(false)
        private set

    private var accumulatedDp = 0f
    private var listAtTop: () -> Boolean = { false }

    /** Opens the island again. */
    fun expand() {
        collapsed = false
        accumulatedDp = 0f
    }

    /**
     * The connection to attach to a parent of the list (Modifier.nestedScroll), so the island sees the scroll the list
     * consumes. [isAtTop] tells whether the list shows its first item; the island opens there.
     */
    fun nestedScroll(isAtTop: () -> Boolean = { false }): NestedScrollConnection {
        listAtTop = isAtTop
        return connection
    }

    private val connection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (consumed.y == 0f) return Offset.Zero
            val step = oruIslandCollapsed(
                current = collapsed,
                delta = consumed.y / density,
                accumulated = accumulatedDp,
                atTop = listAtTop(),
            )
            collapsed = step.collapsed
            accumulatedDp = step.accumulated
            return Offset.Zero
        }
    }
}

/** The state of an island on a screen. It is kept with the screen and follows the density. */
@Composable
fun rememberOruIslandState(): OruIslandState {
    val density = LocalDensity.current.density
    return remember(density) { OruIslandState(density) }
}

/**
 * The room the island takes from the bottom of the screen, not counting the navigation bar: the island, its bottom
 * gap, and the accessory with its gap when there is one. A list that floats under the island takes this as its bottom
 * padding (plus the navigation bar inset), so its last card is never covered.
 */
fun oruIslandClearance(hasAccessory: Boolean): Dp =
    (if (hasAccessory) AccessoryHeight + AccessoryGap else 0.dp) + ExpandedHeight + BottomGap

/**
 * The island. [state] says whether it is collapsed. [expert] is the Simple and Expert choice, changed through
 * [onModeChange]. [selected] is the section that holds the lens; a tap on a section calls [onTabClick] with it. A tap on
 * the collapsed island opens it, and its section is still chosen. [accessory] is the card above the island, and
 * [accessoryCompact] its form beside the collapsed island; without a compact form the accessory fades out as the island
 * collapses.
 *
 * The island is placed at the bottom of its parent, above the navigation bar, with 12 dp more. Its height changes with
 * [accessory]: [oruIslandClearance] gives the room for either.
 */
@Composable
fun OruIsland(
    state: OruIslandState,
    expert: Boolean,
    onModeChange: (Boolean) -> Unit,
    selected: OruIslandTab,
    onTabClick: (OruIslandTab) -> Unit,
    modifier: Modifier = Modifier,
    accessory: (@Composable () -> Unit)? = null,
    accessoryCompact: (@Composable () -> Unit)? = null,
) {
    val reduced = oruReducedMotion()
    val morph = animateFloatAsState(
        targetValue = if (state.collapsed) 1f else 0f,
        // With reduced motion the size cross-fades over the expand fade instead of springing
        animationSpec = if (reduced) tween<Float>(OruMotion.ExpandFadeMillis) else OruMotion.expand(reduced = false),
        label = "oruIslandMorph",
    )
    val spans = remember { IslandSpans() }
    val tick = rememberOruSelectTick()

    // The first change is the state the island opened in, so it is not felt; each later change is one tick
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(state.collapsed) {
        if (settled) tick()
        settled = true
    }

    val hasAccessory = accessory != null
    Layout(
        content = {
            if (accessory != null) {
                IslandAccessory(morph = morph, full = accessory, compact = accessoryCompact)
            }
            IslandBody(
                morph = morph,
                state = state,
                expert = expert,
                onModeChange = onModeChange,
                selected = selected,
                onTabClick = onTabClick,
                spans = spans,
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = BottomGap),
    ) { measurables, constraints ->
        val t = morph.value
        val maxWidth = constraints.maxWidth.toFloat()
        val accessoryRoom = if (hasAccessory) AccessoryHeight.toPx() + AccessoryGap.toPx() else 0f
        val areaHeight = accessoryRoom + ExpandedHeight.toPx()

        val expandedWidth = minOf(IslandMaxWidth.toPx(), maxWidth - IslandSideGap.toPx())
        val islandWidth = lerpFloat(expandedWidth, CollapsedWidth.toPx(), t)
        val islandHeight = lerpFloat(ExpandedHeight.toPx(), CollapsedHeight.toPx(), t)
        val compactSize = CompactSize.toPx()
        val compactGap = CompactGap.toPx()
        // With an accessory, the collapsed island moves right by half the compact card and its gap, so the pair stays centred
        val shift = if (hasAccessory) lerpFloat(0f, (compactSize + compactGap) / 2f, t) else 0f
        val islandLeft = maxWidth / 2f + shift - islandWidth / 2f
        val islandTop = areaHeight - islandHeight
        val islandPlaceable = measurables.last().measure(
            Constraints.fixed(islandWidth.roundToInt(), islandHeight.roundToInt()),
        )

        // Full, the card spans the island's width at the top of the area; compact, it sits left of the island, bottom aligned
        val accessoryWidth = lerpFloat(expandedWidth, compactSize, t)
        val accessoryHeight = lerpFloat(AccessoryHeight.toPx(), compactSize, t)
        val accessoryX = lerpFloat((maxWidth - expandedWidth) / 2f, islandLeft - compactGap - compactSize, t)
        val accessoryY = lerpFloat(0f, areaHeight - compactSize, t)
        val accessoryPlaceable = if (hasAccessory) {
            measurables.first().measure(
                Constraints.fixed(accessoryWidth.roundToInt(), accessoryHeight.roundToInt()),
            )
        } else {
            null
        }

        layout(maxWidth.roundToInt(), areaHeight.roundToInt()) {
            accessoryPlaceable?.place(accessoryX.roundToInt(), accessoryY.roundToInt())
            islandPlaceable.place(islandLeft.roundToInt(), islandTop.roundToInt())
        }
    }
}

/**
 * The glass of the island and of the accessory: a OruGlassSurface with a glint on the lit side that follows the
 * lamp, drawn under [content]. Internal so that the Focus dock can share the same construction.
 */
@Composable
internal fun IslandSurface(
    role: OruGlassRole,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val light = LocalOruLight.current
    val dark = oruIsDark()
    OruGlassSurface(shape = IslandShape, role = role, modifier = modifier) {
        Box(
            Modifier
                .matchParentSize()
                .clip(IslandShape)
                .drawBehind { drawIslandGlint(angle = light.angle, lift = light.lift, dark = dark) },
        )
        content()
    }
}

/** The island's own content: the expanded sections, or the icons when it is collapsed, with the lens under them. */
@Composable
private fun IslandBody(
    morph: State<Float>,
    state: OruIslandState,
    expert: Boolean,
    onModeChange: (Boolean) -> Unit,
    selected: OruIslandTab,
    onTabClick: (OruIslandTab) -> Unit,
    spans: IslandSpans,
) {
    // Composed only while the morph is on its own half, so each form is in the tree only when it is seen
    val expandedShown by remember { derivedStateOf { morph.value < 0.5f } }
    val tick = rememberOruSelectTick()
    val appsLabel = stringResource(R.string.oru_island_apps)
    val settingsLabel = stringResource(R.string.oru_action_settings)
    val modeLabel = stringResource(R.string.oru_mode_label)
    val simpleLabel = stringResource(R.string.oru_mode_simple)
    val expertLabel = stringResource(R.string.oru_mode_expert)
    // The labels are measured at the font scale in use, to decide whether they fit beside the switch
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val tabStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    val modeStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
    val stateText = if (state.collapsed) {
        stringResource(R.string.oru_island_collapsed)
    } else {
        stringResource(R.string.oru_island_expanded)
    }
    // A double tap on a section would open it and then reach the screen it opened; the second tap is ignored here
    val tapGate = remember { OruTapGate() }
    val choose: (OruIslandTab) -> Unit = choose@{ tab ->
        if (!tapGate.accept()) return@choose
        val wasCollapsed = state.collapsed
        if (wasCollapsed) state.expand()
        // The collapse or the expansion is the tick; a choice made while open gets its own
        if (!wasCollapsed && tab != selected) tick()
        onTabClick(tab)
    }

    IslandSurface(
        role = OruGlassRole.Dock,
        modifier = Modifier.semantics { stateDescription = stateText },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(LensInset)
                .onGloballyPositioned { spans.origin(it.positionInRoot().x) },
        ) {
            IslandLens(spans = spans, selected = selected)
            if (expandedShown) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - (morph.value * 2f).coerceIn(0f, 1f) },
                ) {
                    // The tabs take what their labels need and the switch takes the rest. If the three do not fit, the
                    // tabs drop their labels and keep their icons and content descriptions
                    val showLabels = oruIslandShowLabels(
                        availableDp = maxWidth.value,
                        appsLabelDp = measureDp(measurer, appsLabel, tabStyle, density),
                        settingsLabelDp = measureDp(measurer, settingsLabel, tabStyle, density),
                        switchDp = oruConnectedPairDp(
                            firstLabelDp = measureDp(measurer, simpleLabel, modeStyle, density),
                            secondLabelDp = measureDp(measurer, expertLabel, modeStyle, density),
                        ) + 2f * IslandSwitchInset.value,
                    )
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IslandTab(
                            tab = OruIslandTab.Apps,
                            selected = selected == OruIslandTab.Apps,
                            icon = Icons.Outlined.Apps,
                            label = appsLabel,
                            showLabel = showLabels,
                            spans = spans,
                            onClick = { choose(OruIslandTab.Apps) },
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = IslandSwitchInset),
                            contentAlignment = Alignment.Center,
                        ) {
                            OruConnectedPair(
                                groupLabel = modeLabel,
                                firstLabel = simpleLabel,
                                secondLabel = expertLabel,
                                secondSelected = expert,
                                onSelect = { chosen ->
                                    if (chosen != expert) tick()
                                    onModeChange(chosen)
                                },
                            )
                        }
                        IslandTab(
                            tab = OruIslandTab.Settings,
                            selected = selected == OruIslandTab.Settings,
                            icon = Icons.Outlined.Settings,
                            label = settingsLabel,
                            showLabel = showLabels,
                            spans = spans,
                            onClick = { choose(OruIslandTab.Settings) },
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = ((morph.value - 0.5f) * 2f).coerceIn(0f, 1f) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IslandTab(
                        tab = OruIslandTab.Apps,
                        selected = selected == OruIslandTab.Apps,
                        icon = Icons.Outlined.Apps,
                        label = appsLabel,
                        showLabel = false,
                        spans = spans,
                        onClick = { choose(OruIslandTab.Apps) },
                        modifier = Modifier.weight(1f),
                    )
                    IslandTab(
                        tab = OruIslandTab.Settings,
                        selected = selected == OruIslandTab.Settings,
                        icon = Icons.Outlined.Settings,
                        label = settingsLabel,
                        showLabel = false,
                        spans = spans,
                        onClick = { choose(OruIslandTab.Settings) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * One section of the island: its icon, and its label when [showLabel] is set. It is a tab for TalkBack, selected or not,
 * and it is at least 56 dp wide and 48 dp high, so it keeps a full touch target.
 */
@Composable
private fun IslandTab(
    tab: OruIslandTab,
    selected: Boolean,
    icon: ImageVector,
    label: String,
    showLabel: Boolean,
    spans: IslandSpans,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = oruReducedMotion()
    val interactionSource = remember { MutableInteractionSource() }
    val tint by animateColorAsState(
        targetValue = if (selected) oruInk() else MaterialTheme.colorScheme.onSurface,
        animationSpec = OruMotion.ringColor(reduced),
        label = "oruIslandTint",
    )
    Row(
        modifier = modifier
            .fillMaxHeight()
            .defaultMinSize(minWidth = IslandTabMinDp.dp, minHeight = OruSpacing.TouchTarget)
            .onGloballyPositioned { spans.record(tab, it.positionInRoot().x, it.size.width.toFloat()) }
            .clip(IslandShape)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                this.selected = selected
                contentDescription = label
            }
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(IconSize),
        )
        if (showLabel) {
            Spacer(modifier = Modifier.width(LabelGap))
            // Short labels, never truncated: the expanded island drops them before they would not fit (see OruIsland)
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = tint,
                maxLines = 1,
            )
        }
    }
}

/** The width of [text] in [style] at the density of the screen, in dp. */
private fun measureDp(measurer: TextMeasurer, text: String, style: TextStyle, density: Density): Float =
    with(density) { measurer.measure(text, style).size.width.toDp().value }

/**
 * The lens under the selected section: a translucent slab in the app's selected colour, with the lit edge of the depth
 * system. It slides to the section on a spring that settles with a little overshoot; reduced motion moves it on a
 * short tween with no overshoot.
 */
@Composable
private fun BoxScope.IslandLens(spans: IslandSpans, selected: OruIslandTab) {
    val reduced = oruReducedMotion()
    val target = spans.spanOf(selected)
    val spec: FiniteAnimationSpec<Float> = if (reduced) tween(OruMotion.ExpandFadeMillis) else LensSpring
    val lensLeft by animateFloatAsState(
        targetValue = target?.left ?: 0f,
        animationSpec = spec,
        label = "oruIslandLensLeft",
    )
    val lensWidth by animateFloatAsState(
        targetValue = target?.width ?: 0f,
        animationSpec = spec,
        label = "oruIslandLensWidth",
    )
    val widthDp = with(LocalDensity.current) { lensWidth.coerceAtLeast(0f).toDp() }
    Box(
        Modifier
            .align(Alignment.CenterStart)
            .offset { IntOffset(lensLeft.roundToInt(), 0) }
            .width(widthDp)
            .fillMaxHeight()
            .oruSlab(
                shape = IslandShape,
                color = oruSelectedFill().copy(alpha = LensAlpha),
                elevation = OruElevation.Raised,
                shadow = false,
            ),
    )
}

/**
 * The accessory card above the island. Its full form is composed while the morph is in its first half and its compact
 * form in the second; the card itself morphs between the two sizes in the island's layout (see [OruIsland]).
 */
@Composable
private fun IslandAccessory(
    morph: State<Float>,
    full: @Composable () -> Unit,
    compact: (@Composable () -> Unit)?,
) {
    val fullShown by remember { derivedStateOf { morph.value < 0.5f } }
    // Without a compact form the card has nothing to show when small, so it fades out as the island collapses
    val fade = if (compact == null) {
        Modifier.graphicsLayer { alpha = 1f - morph.value }
    } else {
        Modifier
    }
    IslandSurface(role = OruGlassRole.Card, modifier = fade) {
        if (fullShown) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = (1f - morph.value * 2f).coerceIn(0f, 1f) },
                contentAlignment = Alignment.Center,
            ) {
                full()
            }
        } else if (compact != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = ((morph.value - 0.5f) * 2f).coerceIn(0f, 1f) },
                contentAlignment = Alignment.Center,
            ) {
                compact()
            }
        }
    }
}

/**
 * The run accessory: the app being patched, "Patching <app>", how many of its patches are done, and a thin accent bar of
 * the progress. A tap opens the run. [progress] is 0 to 1, or null while it is not known. [icon] is the app's icon; the
 * default is a placeholder. It fills the width it is given; the island gives it a card.
 */
@Composable
fun OruRunAccessory(
    appName: String,
    progress: Float?,
    done: Int,
    total: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** A finished run waiting to be installed: the accessory names that, and a tap opens the install. */
    ready: Boolean = false,
    icon: @Composable () -> Unit = { OruIslandAppPlaceholder() },
) {
    val description = if (ready) {
        stringResource(R.string.oru_island_run_ready_description, appName)
    } else {
        stringResource(R.string.oru_island_run_description, appName, done, total)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = OruSpacing.TouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = if (ready) {
                    stringResource(R.string.oru_island_run_ready_title, appName)
                } else {
                    stringResource(R.string.oru_island_run_title, appName)
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (ready) {
                    stringResource(R.string.oru_island_run_ready_hint)
                } else {
                    stringResource(R.string.oru_island_run_progress, done, total)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            RunProgressBar(progress = progress)
        }
    }
}

/** The compact form of the run accessory, beside the collapsed island: a ring of the progress. */
@Composable
fun OruRunAccessoryCompact(
    appName: String,
    progress: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.oru_island_run_title, appName)
    val color = oruInk()
    Box(
        modifier = modifier
            .size(CompactSize)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (progress == null) {
            CircularProgressIndicator(
                modifier = Modifier.size(RingSize),
                color = color,
                strokeWidth = RingStroke,
            )
        } else {
            CircularProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.size(RingSize),
                color = color,
                strokeWidth = RingStroke,
            )
        }
    }
}

@Composable
private fun RunProgressBar(progress: Float?) {
    val color = oruInk()
    val track = color.copy(alpha = TrackAlpha)
    val barModifier = Modifier
        .fillMaxWidth()
        .height(RunBarHeight)
    if (progress == null) {
        LinearProgressIndicator(modifier = barModifier, color = color, trackColor = track)
    } else {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = barModifier,
            color = color,
            trackColor = track,
        )
    }
}

@Composable
private fun OruIslandAppPlaceholder() {
    Box(
        modifier = Modifier
            .size(PlaceholderSize)
            .clip(RoundedCornerShape(PlaceholderCorner))
            .background(oruSelectedFill().copy(alpha = PlaceholderAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Apps,
            contentDescription = null,
            tint = oruInk(),
            modifier = Modifier.size(PlaceholderIcon),
        )
    }
}

/**
 * Where each section is drawn, in the root of the screen, and where the island's content starts. The lens reads these
 * to slide to the chosen section; they are state, so the lens follows a section that moves during the morph.
 */
private class IslandSpans {
    private val sections = mutableStateMapOf<OruIslandTab, IslandSpan>()
    private var originX by mutableFloatStateOf(0f)

    fun origin(x: Float) {
        if (originX != x) originX = x
    }

    /** Records the section [tab] at [rootLeft] in the root, [width] wide. */
    fun record(tab: OruIslandTab, rootLeft: Float, width: Float) {
        val span = IslandSpan(rootLeft, width)
        if (sections[tab] != span) sections[tab] = span
    }

    /** The span of [tab] from the left of the island's content, or null before the section has been placed. */
    fun spanOf(tab: OruIslandTab): IslandSpan? {
        val raw = sections[tab] ?: return null
        return IslandSpan(raw.left - originX, raw.width)
    }
}

private data class IslandSpan(val left: Float, val width: Float)

/** The glint: a soft white light on the lit side of the island, brighter when the phone leans toward the lamp. */
private fun DrawScope.drawIslandGlint(angle: Float, lift: Float, dark: Boolean) {
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return
    val alpha = (if (dark) GlintDark else GlintLight) * (1f + GlintLift * lift.coerceIn(-1f, 1f))
    val centre = Offset(
        x = width / 2f + cos(angle) * width * GlintReachX,
        y = height / 2f + sin(angle) * height * GlintReachY,
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = alpha), Color.Transparent),
            center = centre,
            radius = width * 0.5f,
        ),
    )
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction

// The expanded island is up to 420 dp wide and keeps 12 dp clear on each side of the screen
private val IslandMaxWidth = 420.dp
private val IslandSideGap = 24.dp
private val CollapsedWidth = 160.dp
private val ExpandedHeight = 64.dp
private val CollapsedHeight = 56.dp
private val BottomGap = 12.dp
private val AccessoryGap = 8.dp
private val AccessoryHeight = 64.dp
private val CompactSize = 56.dp
private val CompactGap = 8.dp
private val LensInset = 4.dp
private val IconSize = 22.dp
private val LabelGap = 6.dp
// The Simple and Expert switch's margin on each side, inside the lens inset
private val IslandSwitchInset = 4.dp
// The tab's width in dp, without its label, is 2 x 12 dp padding + the 22 dp icon + the 6 dp gap (see IslandTab)
private const val IslandTabChromeDp = 52f
private const val IslandTabMinDp = 56f
private val RingSize = 28.dp
private val RingStroke = 3.dp
private val RunBarHeight = 4.dp
private val PlaceholderSize = 36.dp
private val PlaceholderIcon = 20.dp
private val PlaceholderCorner = 10.dp
private const val PlaceholderAlpha = 0.2f
private const val TrackAlpha = 0.2f

/** Every capsule of the island is a full half circle at each end, so the corner follows the height as it morphs. */
private val IslandShape = RoundedCornerShape(percent = 50)

/** The lens spring: a little overshoot as it settles. */
private val LensSpring = oruSpring<Float>(response = OruMotion.SoftResponseSeconds, damping = 0.72f)

private const val LensAlpha = 0.16f
private const val GlintLight = 0.22f
private const val GlintDark = 0.12f
private const val GlintLift = 0.3f
private const val GlintReachX = 0.42f
private const val GlintReachY = 0.9f
