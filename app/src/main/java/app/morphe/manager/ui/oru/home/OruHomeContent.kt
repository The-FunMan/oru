/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.oru.components.OruAppCard
import app.morphe.manager.ui.oru.components.OruAppTile
import app.morphe.manager.ui.oru.components.OruBackdropHost
import app.morphe.manager.ui.oru.components.OruBanner
import app.morphe.manager.ui.oru.components.OruCardButton
import app.morphe.manager.ui.oru.components.OruCardUi
import app.morphe.manager.ui.oru.components.OruChip
import app.morphe.manager.ui.oru.components.OruChipUi
import app.morphe.manager.ui.oru.components.OruGlassForm
import app.morphe.manager.ui.oru.components.OruGlassSurface
import app.morphe.manager.ui.oru.components.OruIsland
import app.morphe.manager.ui.oru.components.OruIslandTab
import app.morphe.manager.ui.oru.components.OruNoticeAction
import app.morphe.manager.ui.oru.components.OruRunAccessory
import app.morphe.manager.ui.oru.components.OruRunAccessoryCompact
import app.morphe.manager.ui.oru.components.OruOrbBackdrop
import app.morphe.manager.ui.oru.components.OruOutlinedButton
import app.morphe.manager.ui.oru.components.OruPrimaryButton
import app.morphe.manager.ui.oru.components.OruSkeleton
import app.morphe.manager.ui.oru.components.OruTileSource
import app.morphe.manager.ui.oru.components.oruAmbient
import app.morphe.manager.ui.oru.components.oruEntrance
import app.morphe.manager.ui.oru.components.rememberOruEntranceLedger
import app.morphe.manager.ui.oru.components.OruTone
import app.morphe.manager.ui.oru.components.OruWordmark
import app.morphe.manager.ui.oru.components.oruGlassShape
import app.morphe.manager.ui.oru.components.oruOrbPalette
import app.morphe.manager.ui.oru.components.oruIslandClearance
import app.morphe.manager.ui.oru.components.oruReducedMotion
import app.morphe.manager.ui.oru.components.rememberOruAppTint
import app.morphe.manager.ui.oru.components.rememberOruIslandState
import app.morphe.manager.ui.oru.nav.oruLeaveDrop
import app.morphe.manager.ui.oru.nav.oruLeaveParting
import app.morphe.manager.ui.oru.nav.oruTileSharedKey
import app.morphe.manager.ui.oru.theme.OruBlobShape
import app.morphe.manager.ui.oru.theme.OruSpacing
import kotlinx.coroutines.launch

/**
 * The Home screen body: a glass top bar floating over the list, the title, one banner, one card per supported app,
 * and the floating island at the bottom with the sections and the Simple and Expert switch. Holds no state of its own
 * beyond the list and the island's scroll. Every action is a lambda, so the screen decides what it means.
 *
 * [settingsShown] is whether the settings sheet is open: the island's Settings section is chosen then. [run] is the
 * batch or single-app run in progress, if any: its accessory sits above the island, and [onRunClick] opens that run.
 *
 * [overlay] is drawn above the list and the island in the same backdrop host as the glass bar, so a sheet passed there
 * blurs the list too and covers the island.
 */
@Composable
fun OruHomeContent(
    state: OruHomeUiState,
    refreshing: Boolean,
    settingsShown: Boolean,
    run: OruHomeRun?,
    onRefresh: () -> Unit,
    onSettingsClick: () -> Unit,
    onRunClick: () -> Unit,
    onModeChange: (Boolean) -> Unit,
    onBannerAction: (OruHomeBanner) -> Unit,
    onCardClick: (OruHomeCard) -> Unit,
    onCardLongClick: (OruHomeCard) -> Unit,
    onSourceClick: (OruHomeCard) -> Unit,
    onPrimary: (OruHomeCard) -> Unit,
    onSecondary: (OruHomeCard) -> Unit,
    onCheckUpdates: () -> Unit,
    onAddSource: () -> Unit,
    // The settings sheet, drawn in the backdrop host so its glass blurs the Home content
    overlay: @Composable BoxScope.() -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // The cards' entrance plays once per Home screen
    val entrance = rememberOruEntranceLedger()
    val listState = rememberLazyListState()
    val islandState = rememberOruIslandState()
    val scope = rememberCoroutineScope()
    // The large wordmark travels into the glass bar as the list scrolls, and the bar's glass comes in after it. One progress,
    // read from the scroll, drives the wordmark, the lines below it and the glass (see OruTitleCollapse.kt). Reduced
    // motion keeps the progress and drops the travel: the wordmark and the bar's wordmark cross-fade where they are
    val reduced = oruReducedMotion()
    val fallbackCollapsePx = with(LocalDensity.current) { TitleCollapseFallback.toPx() }
    val titleAnchor = remember(fallbackCollapsePx) { OruTitleAnchor(fallbackCollapsePx) }
    val titleProgress = remember(listState, titleAnchor) {
        derivedStateOf {
            oruTitleProgress(
                firstVisibleItem = listState.firstVisibleItemIndex,
                scrollOffsetPx = listState.firstVisibleItemScrollOffset,
                collapsePx = titleAnchor.collapsePx,
            )
        }
    }
    val glassPresence = remember(titleProgress) {
        derivedStateOf { oruTitlePhase(titleProgress.value, GlassStart, 1f) }
    }
    val subtitleAlpha: () -> Float = { 1f - oruTitlePhase(titleProgress.value, 0f, SubtitleFadeEnd) }
    // While the travelling copy is drawn, the in-list title is hidden. Only once the anchors are measured, so the title
    // is never missing for a frame. Reduced motion has no copy: the in-list title fades out where it is
    val titleAlpha: () -> Float = {
        when {
            reduced -> 1f - titleProgress.value
            titleAnchor.ready -> 0f
            else -> 1f
        }
    }
    val barTitleAlpha: () -> Float = { if (reduced) titleProgress.value else 0f }
    val titleTravelScale = oruWordmarkTravelScale(headerScale = HeaderWordmarkScale, barScale = BarWordmarkScale)
    // The island holds the sections: Apps while the list is shown, Settings while its sheet is open
    val selected = if (settingsShown) OruIslandTab.Settings else OruIslandTab.Apps
    val onTabClick: (OruIslandTab) -> Unit = { tab ->
        when (tab) {
            OruIslandTab.Apps -> scope.launch { listState.animateScrollToItem(0) }
            OruIslandTab.Settings -> onSettingsClick()
        }
    }
    // The bottom of the list clears the island, its accessory when there is a run, and the navigation bar under it
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listBottom = oruIslandClearance(hasAccessory = run != null) + OruSpacing.Large + navBarBottom

    // The orbs take the primary and the accents of the first apps; the glass on the list blurs and tints them
    val orbColors = rememberHomeOrbColors(state.cards)

    // The card that opened Focus. Its neighbours leave in its direction, so the tap is remembered before the navigation
    var tappedId by remember { mutableStateOf<String?>(null) }
    val tappedIndex = state.cards.indexOfFirst { it.id == tappedId }.takeIf { it >= 0 }

    OruBackdropHost(
        // The glow is drawn before the status bar padding so it also covers the status bar area
        modifier = modifier.fillMaxSize().oruAmbient(MaterialTheme.colorScheme.primary).statusBarsPadding(),
        backdrop = {
            // The cards and the toggles are glass too, so the list sits in a host of its own over the orbs: they blur
            // the orbs, while the bar and the sheet, in the outer host, blur the whole list
            OruBackdropHost(
                modifier = Modifier.fillMaxSize(),
                backdrop = {
                    // The orbs lie behind the list and move a tenth as far as it scrolls. The items above the first visible
                    // one are not measured, so each counts as the mean of the visible ones
                    OruOrbBackdrop(
                        colors = orbColors,
                        modifier = Modifier.fillMaxSize(),
                        scroll = { oruListScrollPx(listState) },
                    )
                },
                floating = {
                    PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = onRefresh,
                        // The island follows the list's scroll from a parent of the list, and opens at the top of it
                        modifier = Modifier.fillMaxSize().nestedScroll(
                            islandState.nestedScroll {
                                listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                            },
                        ),
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            // The top padding clears the glass bar, so the header starts below it. The bottom one clears
                            // the island, with its accessory, and the navigation bar, so the last card is never under it
                            contentPadding = PaddingValues(
                                start = OruSpacing.ScreenGutter,
                                top = OruSpacing.Large + HomeBarInset + TopBarHeight,
                                end = OruSpacing.ScreenGutter,
                                bottom = listBottom,
                            ),
                            verticalArrangement = Arrangement.spacedBy(OruSpacing.Medium),
                        ) {
                            item(key = "header") {
                                HomeHeader(
                                    listState = listState,
                                    anchor = titleAnchor,
                                    titleAlpha = titleAlpha,
                                    subtitleAlpha = subtitleAlpha,
                                    expert = state.expert,
                                )
                            }

                            state.banner?.let { banner ->
                                item(key = "banner") {
                                    HomeBanner(banner = banner, onAction = { onBannerAction(banner) })
                                }
                            }

                            if (state.loading && state.cards.isEmpty()) {
                                items(count = SkeletonCount, key = { "skeleton$it" }) {
                                    OruSkeleton(modifier = Modifier.fillMaxWidth().height(SkeletonHeight))
                                }
                            } else if (state.empty) {
                                item(key = "empty") {
                                    HomeEmpty(offline = state.offline, onCheckUpdates = onCheckUpdates, onAddSource = onAddSource)
                                }
                            }

                            itemsIndexed(state.cards, key = { _, card -> card.id }) { index, card ->
                                OruAppCard(
                                    modifier = Modifier
                                        .oruEntrance(card.id, index, entrance)
                                        .oruLeaveParting(index, tappedIndex),
                                    ui = card.toCardUi(expert = state.expert),
                                    onClick = {
                                        tappedId = card.id
                                        onCardClick(card)
                                    },
                                    onLongClick = { onCardLongClick(card) },
                                    onSourceClick = if (card.source != null) {
                                        { onSourceClick(card) }
                                    } else {
                                        null
                                    },
                                    onPrimary = {
                                        tappedId = card.id
                                        onPrimary(card)
                                    },
                                    onSecondary = if (card.secondary != null) {
                                        {
                                            tappedId = card.id
                                            onSecondary(card)
                                        }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        floating = {
            HomeTopBar(presence = glassPresence.value, anchor = titleAnchor, titleAlpha = barTitleAlpha)
            // The title is drawn here, above the bar's glass, so it is never clipped by the list and lands in the bar
            if (!reduced) {
                HomeTitleTravel(anchor = titleAnchor, progress = { titleProgress.value }, toScale = titleTravelScale)
            }
            // The island is the last Home content for TalkBack, after the list; the sheet is drawn above it
            OruIsland(
                state = islandState,
                expert = state.expert,
                onModeChange = onModeChange,
                selected = selected,
                onTabClick = onTabClick,
                // The island drops and fades as Focus opens, see oruLeaveDrop
                modifier = Modifier.align(Alignment.BottomCenter).oruLeaveDrop(),
                accessory = if (run == null) {
                    null
                } else {
                    {
                        OruRunAccessory(
                            appName = run.appName,
                            progress = run.progress,
                            done = run.done,
                            total = run.total,
                            onClick = onRunClick,
                            ready = run.ready,
                            icon = {
                                OruAppTile(
                                    source = OruTileSource(name = run.appName, packageInfo = run.packageInfo),
                                    size = RunIconSize,
                                    shape = OruBlobShape.presetFor(run.packageName),
                                    sharedKey = null,
                                )
                            },
                        )
                    }
                },
                accessoryCompact = if (run == null) {
                    null
                } else {
                    {
                        OruRunAccessoryCompact(
                            appName = run.appName,
                            progress = run.progress,
                            onClick = onRunClick,
                        )
                    }
                },
            )
            overlay()
        },
    )
}

/**
 * The colours of the Home orbs: the primary, then the accents of the first [OrbAppCount] cards. An app with no
 * colour in its icon already has the primary as its accent (rememberOruAppTint). A short list is filled with the
 * inverse primary, a second hue of the same family.
 */
@Composable
private fun rememberHomeOrbColors(cards: List<OruHomeCard>): List<Color> {
    val scheme = MaterialTheme.colorScheme
    val accents = ArrayList<Color>(OrbAppCount)
    for (card in cards.take(OrbAppCount)) {
        key(card.id) { accents.add(rememberOruAppTint(card.item.packageInfo).accent) }
    }
    return remember(scheme.primary, scheme.inversePrimary, accents) {
        oruOrbPalette(
            primary = scheme.primary,
            accents = accents,
            fallbacks = listOf(scheme.inversePrimary, scheme.primary, scheme.inversePrimary),
        )
    }
}

/**
 * The glass top bar, a dock-form pill inset from the screen edges, with the wordmark at its normal size. [presence] fades
 * its glass in over the second half of the collapse. [titleAlpha] shows the bar's own wordmark: it stays hidden while the
 * travelling wordmark lands on it, and cross-fades in under reduced motion. The sections and the Simple and Expert switch
 * are in the island at the bottom.
 */
@Composable
private fun BoxScope.HomeTopBar(presence: Float, anchor: OruTitleAnchor, titleAlpha: () -> Float) {
    OruGlassSurface(
        shape = oruGlassShape(OruGlassForm.Dock),
        presence = presence,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(start = OruSpacing.ScreenGutter, top = HomeBarInset, end = OruSpacing.ScreenGutter),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TopBarHeight)
                .padding(horizontal = BarInner),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The slot the travelling wordmark lands on. Its semantics are cleared: the in-list copy is the one TalkBack reads
            OruWordmark(
                modifier = Modifier
                    .onGloballyPositioned { anchor.onBarMeasured(it.positionInRoot()) }
                    .graphicsLayer { alpha = titleAlpha() }
                    .clearAndSetSemantics { },
            )
        }
    }
}

/**
 * The large wordmark at the top of the list, with the title and the subtitle below it. The wordmark is measured for the
 * collapse, then drawn by [HomeTitleTravel] instead, so it is hidden here by [titleAlpha]. The title and the subtitle fade
 * by [subtitleAlpha]. The list adds 12 dp below the header, so 6 dp here makes the gap to the first card 18 dp.
 */
@Composable
private fun HomeHeader(
    listState: LazyListState,
    anchor: OruTitleAnchor,
    titleAlpha: () -> Float,
    subtitleAlpha: () -> Float,
    expert: Boolean,
) {
    Column(modifier = Modifier.padding(top = HeaderTopSpace, bottom = HeaderBottomSpace)) {
        Box(
            modifier = Modifier.onGloballyPositioned {
                anchor.onHeaderMeasured(
                    position = it.positionInRoot(),
                    firstVisibleItem = listState.firstVisibleItemIndex,
                    scrollOffsetPx = listState.firstVisibleItemScrollOffset,
                )
            },
        ) {
            OruWordmark(
                modifier = Modifier.graphicsLayer { alpha = titleAlpha() },
                scale = HeaderWordmarkScale,
            )
        }
        Spacer(modifier = Modifier.height(TitleLedeSpace))
        Text(
            text = stringResource(R.string.oru_home_title),
            modifier = Modifier.semantics { heading() }.graphicsLayer { alpha = subtitleAlpha() },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(TitleLedeSpace))
        Text(
            text = stringResource(R.string.oru_home_subtitle),
            modifier = Modifier.graphicsLayer { alpha = subtitleAlpha() },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The mode the cards are in, under what the list says: Expert only, as Simple is the default reading
        if (expert) {
            Spacer(modifier = Modifier.height(TitleLedeSpace))
            OruChip(
                text = stringResource(R.string.oru_mode_expert),
                tone = OruTone.Neutral,
                modifier = Modifier.graphicsLayer { alpha = subtitleAlpha() },
            )
        }
    }
}

/**
 * The travelling copy of the wordmark, drawn above the glass bar. It is the header's wordmark, so it starts exactly over the
 * in-list one, and moves and shrinks to the bar's wordmark as [progress] runs from 0 to 1. [toScale] is the scale at 1 (the
 * bar's size against the header's). The scale pivots on the copy's top left, so the mark stays crisp and its top left lands
 * on the bar wordmark's top left. It has no semantics and takes no input.
 */
@Composable
private fun BoxScope.HomeTitleTravel(anchor: OruTitleAnchor, progress: () -> Float, toScale: Float) {
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .onGloballyPositioned { anchor.onOverlayMeasured(it.positionInRoot()) },
    ) {
        OruWordmark(
            modifier = Modifier
                .clearAndSetSemantics { }
                .graphicsLayer {
                    val step = anchor.travel(progress(), toScale = toScale)
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = step.x
                    translationY = step.y
                    scaleX = step.scale
                    scaleY = step.scale
                    alpha = if (anchor.ready) 1f else 0f
                },
            scale = HeaderWordmarkScale,
        )
    }
}

@Composable
private fun HomeBanner(banner: OruHomeBanner, onAction: () -> Unit) {
    val label = bannerActionLabel(banner)
    OruBanner(
        text = bannerText(banner),
        action = label?.let { OruNoticeAction(label = stringResource(it), onClick = onAction) },
        leading = bannerLeading(banner),
    )
}

/**
 * The empty list. Offline, the list is empty because the patch list could not be read, so the copy names the connection
 * and the one action is to try again; a source cannot fix that.
 */
@Composable
private fun HomeEmpty(offline: Boolean, onCheckUpdates: () -> Unit, onAddSource: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = OruSpacing.XLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OruSpacing.Medium),
    ) {
        Text(
            text = stringResource(if (offline) R.string.oru_error_no_connection else R.string.oru_home_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (offline) {
            OruPrimaryButton(text = stringResource(R.string.oru_action_try_again), onClick = onCheckUpdates)
        } else {
            OruPrimaryButton(text = stringResource(R.string.oru_home_check_updates), onClick = onCheckUpdates)
            OruOutlinedButton(text = stringResource(R.string.oru_home_add_source), onClick = onAddSource)
        }
    }
}

@Composable
private fun bannerText(banner: OruHomeBanner): String = when (banner) {
    is OruHomeBanner.ManagerUpdate -> managerUpdateText(banner.update)
    OruHomeBanner.SourcesAttention -> stringResource(R.string.oru_banner_sources_attention)
    OruHomeBanner.Metered -> stringResource(R.string.oru_banner_metered)
    is OruHomeBanner.BatchRunning ->
        stringResource(R.string.oru_home_banner_batch_running, banner.processed, banner.total)
    OruHomeBanner.BatchFinished -> stringResource(R.string.oru_home_banner_batch_finished)
    is OruHomeBanner.BundleUpdate -> stringResource(
        when (banner.state) {
            OruBundleUpdateState.Updating -> R.string.oru_banner_bundle_updating
            OruBundleUpdateState.Done -> R.string.oru_banner_bundle_done
            OruBundleUpdateState.Failed -> R.string.oru_banner_bundle_failed
        },
    )
    is OruHomeBanner.Repatch ->
        pluralStringResource(R.plurals.oru_banner_repatch, banner.count, banner.count)
}

/** The line the banner says about the Oru update, in the state the update is in. */
@Composable
private fun managerUpdateText(update: OruManagerUpdate): String = when (update) {
    is OruManagerUpdate.Available -> stringResource(R.string.oru_update_banner_available, update.version)
    is OruManagerUpdate.Downloading -> update.percent
        ?.let { stringResource(R.string.oru_update_banner_downloading_percent, it) }
        ?: stringResource(R.string.oru_update_banner_downloading)
    OruManagerUpdate.Ready -> stringResource(R.string.oru_update_banner_ready)
    OruManagerUpdate.Installing -> stringResource(R.string.oru_update_banner_installing)
    is OruManagerUpdate.Failed -> stringResource(R.string.oru_update_banner_failed, update.message)
}

/** The Oru update's action, or null while it only tells. */
private fun managerUpdateActionLabel(update: OruManagerUpdate): Int? = when (update) {
    is OruManagerUpdate.Available -> R.string.oru_banner_action_update
    OruManagerUpdate.Ready -> R.string.oru_update_action_install
    is OruManagerUpdate.Failed -> R.string.oru_banner_action_retry
    is OruManagerUpdate.Downloading, OruManagerUpdate.Installing -> null
}

/** The banner's one action, or null when it only tells. */
private fun bannerActionLabel(banner: OruHomeBanner): Int? = when (banner) {
    is OruHomeBanner.ManagerUpdate -> managerUpdateActionLabel(banner.update)
    is OruHomeBanner.Repatch -> R.string.oru_banner_action_update
    OruHomeBanner.SourcesAttention,
    OruHomeBanner.Metered,
    is OruHomeBanner.BatchRunning,
    OruHomeBanner.BatchFinished -> R.string.oru_banner_action_review
    is OruHomeBanner.BundleUpdate ->
        if (banner.state == OruBundleUpdateState.Failed) R.string.oru_banner_action_retry else null
}

/** A spinner while the sources update on their own, so it reads as progress and not as a request. */
private fun bannerLeading(banner: OruHomeBanner): (@Composable () -> Unit)? {
    if (banner is OruHomeBanner.ManagerUpdate) {
        val busy = banner.update is OruManagerUpdate.Downloading || banner.update == OruManagerUpdate.Installing
        return if (busy) {
            { BannerSpinner() }
        } else {
            null
        }
    }
    if (banner !is OruHomeBanner.BundleUpdate) return null
    return when (banner.state) {
        OruBundleUpdateState.Updating -> {
            { BannerSpinner() }
        }
        OruBundleUpdateState.Done -> {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(BannerMarkSize),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        OruBundleUpdateState.Failed -> null
    }
}

private val BannerMarkSize = 18.dp
private val BannerMarkStroke = 2.dp

@Composable
private fun BannerSpinner() {
    CircularProgressIndicator(
        modifier = Modifier.size(BannerMarkSize),
        strokeWidth = BannerMarkStroke,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * Turns a decided card into the component model. The presenter has already decided what each mode carries (see
 * `OruHomePresenter.card`); this only chooses the words. Simple shows the one sentence and the Customized badge,
 * Expert shows the status chip, the version chips and the benefit line.
 */
@Composable
private fun OruHomeCard.toCardUi(expert: Boolean): OruCardUi {
    val name = item.nameSuffix?.let { "${item.displayName} ($it)" } ?: item.displayName
    return OruCardUi(
        id = id,
        tile = OruTileSource(name = name, packageInfo = item.packageInfo),
        shape = OruBlobShape.presetFor(id),
        sharedKey = oruTileSharedKey(id),
        benefit = if (expert) benefitText() else sentenceText(),
        chips = if (expert) expertChips() else simpleChips(),
        source = source,
        hint = hintText(),
        primary = OruCardButton(label = stringResource(primary.labelRes(expert)), style = primary.style()),
        secondary = secondary?.let { OruCardButton(label = stringResource(it.labelRes(expert)), style = it.style()) },
    )
}

@Composable
private fun OruHomeCard.benefitText(): String = when {
    patchCount > 0 -> pluralStringResource(R.plurals.oru_home_benefit_patches, patchCount, patchCount)
    version != null -> stringResource(R.string.oru_state_version, version)
    else -> ""
}

/** The one sentence of Simple. The APK sentence names the app and the version the APK has to be. */
@Composable
private fun OruHomeCard.sentenceText(): String = when (status.simpleSentence()) {
    OruHomeSentence.Checking -> stringResource(R.string.oru_state_checking_phone)
    OruHomeSentence.NotChecked -> stringResource(R.string.oru_simple_status_not_checked)
    OruHomeSentence.NeedsApk -> version?.let { stringResource(R.string.oru_simple_status_needs, item.displayName, it) }
        ?: stringResource(R.string.oru_simple_status_needs_plain, item.displayName)
    OruHomeSentence.WaitingApk -> stringResource(R.string.oru_chip_waiting_apk)
    OruHomeSentence.SavedCopyGated -> version?.let { stringResource(R.string.oru_simple_status_gated, item.displayName, it) }
        ?: stringResource(R.string.oru_simple_status_gated_plain)
    OruHomeSentence.Ready -> stringResource(R.string.oru_simple_status_ready)
    OruHomeSentence.UpdateReady -> stringResource(R.string.oru_simple_status_update_ready)
    OruHomeSentence.UpToDate -> stringResource(R.string.oru_simple_status_up_to_date)
    OruHomeSentence.Patched -> stringResource(R.string.oru_simple_status_patched)
}

@Composable
private fun OruHomeCard.simpleChips(): List<OruChipUi> = buildList {
    if (customized) add(OruChipUi(text = stringResource(R.string.oru_simple_customized), tone = OruTone.Neutral))
}

@Composable
private fun OruHomeCard.expertChips(): List<OruChipUi> = buildList {
    add(OruChipUi(text = stringResource(statusTextRes()), tone = chipTone))
    // An app that can be updated shows the patched copy beside the newest version the sources support
    if (status == OruHomeCardStatus.UpdateAvailable) {
        version?.let { add(OruChipUi(text = stringResource(R.string.oru_chip_patched_version, it), tone = OruTone.Neutral, mono = true)) }
        latestVersion?.let { add(OruChipUi(text = stringResource(R.string.oru_chip_latest_version, it), tone = OruTone.Neutral, mono = true)) }
    } else {
        version?.let { add(OruChipUi(text = it, tone = OruTone.Neutral, mono = true)) }
    }
}

@Composable
private fun OruHomeCard.hintText(): String? = when (hint) {
    null -> null
    OruHomeHint.ApkMirror ->
        hintVersion?.let { stringResource(R.string.oru_hint_apkmirror, it) }
            ?: stringResource(R.string.oru_hint_return_download)
    OruHomeHint.ReturnDownload -> stringResource(R.string.oru_hint_return_download)
    OruHomeHint.SavedCopy -> stringResource(R.string.oru_state_saved_copy)
    OruHomeHint.SavedCopyGated ->
        hintVersion?.let { stringResource(R.string.oru_hint_saved_gated, it) }
            ?: stringResource(R.string.oru_hint_saved_gated_plain)
}

private fun OruHomeCard.statusTextRes(): Int = when (status) {
    OruHomeCardStatus.Checking -> R.string.oru_chip_checking
    OruHomeCardStatus.Unverified -> R.string.oru_chip_unverified
    OruHomeCardStatus.NeedsApk,
    OruHomeCardStatus.SavedCopyGated -> R.string.oru_chip_apk_needed
    OruHomeCardStatus.Waiting -> R.string.oru_chip_waiting_apk
    OruHomeCardStatus.SavedCopy,
    OruHomeCardStatus.ReadyOnDevice -> R.string.oru_chip_ready
    OruHomeCardStatus.UnpatchedInstalled -> R.string.oru_state_unpatched_installed
    OruHomeCardStatus.UpdateAvailable -> R.string.oru_state_update_available
    OruHomeCardStatus.Installed,
    OruHomeCardStatus.PatchedElsewhere -> R.string.oru_chip_installed
}

/** The label of an action. Expert's shorter label for the saved copy fits one line; Simple keeps the full one. */
private fun OruHomeAction.labelRes(expert: Boolean): Int = when (this) {
    OruHomeAction.AppInfo -> R.string.oru_action_app_info
    OruHomeAction.CheckAndPatch -> R.string.oru_action_check_and_patch
    OruHomeAction.FindOnApkMirror -> R.string.oru_action_find_apkmirror
    OruHomeAction.UseFile -> R.string.oru_action_use_file
    OruHomeAction.ChooseFile -> R.string.oru_action_choose_file
    OruHomeAction.OpenApkMirrorAgain -> R.string.oru_action_open_apkmirror_again
    OruHomeAction.PatchFromSaved ->
        if (expert) R.string.oru_action_patch_saved_expert else R.string.oru_action_patch_from_saved
    OruHomeAction.UseAnotherFile -> R.string.oru_action_use_another_file
    OruHomeAction.GetIt -> R.string.oru_action_get_it
    OruHomeAction.Patch -> R.string.oru_action_patch
    OruHomeAction.Update -> R.string.oru_action_update
    OruHomeAction.Open -> R.string.oru_action_open
    OruHomeAction.PatchAgain -> R.string.oru_action_patch_again
}

// Header spacing: the list adds 12 dp below the header, so 6 dp here makes 18.
private val HeaderTopSpace = 8.dp
private val TitleLedeSpace = 6.dp
private val HeaderBottomSpace = 6.dp
// The scroll at which the title reaches the bar, used until both are measured. At rest the title's top is 96 dp below the
// top of the list, and the bar's title is 26 dp below the top of the bar's area, so the gap is 70 dp
private val TitleCollapseFallback = 70.dp
// The collapse in two halves: the subtitle fades out over the first, and the bar's glass comes in over the second
private const val SubtitleFadeEnd = 0.5f
private const val GlassStart = 0.5f
/** The height of the glass top bar. */
private val TopBarHeight = 64.dp
/** The gap between the screen edge and the glass bar, at the top. */
private val HomeBarInset = 8.dp
/** The inner padding of the glass bar, around the brand dot and the title. */
private val BarInner = 16.dp
// The wordmark is drawn about 49 sp (1.8 times its 27 sp bar size) in the header, and at its normal size in the bar
private const val HeaderWordmarkScale = 1.8f
private const val BarWordmarkScale = 1f
/** The app icon of the run accessory. */
private val RunIconSize = 36.dp
private val SkeletonHeight = 160.dp
private const val SkeletonCount = 3

/** How many of the first cards lend their accent to an orb. */
private const val OrbAppCount = 3

/** How far [state] has scrolled, in pixels, estimated with the mean height of the items on screen. Read in a draw phase. */
private fun oruListScrollPx(state: LazyListState): Float {
    val visible = state.layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return 0f
    var total = 0
    for (item in visible) total += item.size
    return state.firstVisibleItemIndex * (total.toFloat() / visible.size) + state.firstVisibleItemScrollOffset
}
