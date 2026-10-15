/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.ui.oru.components.OruFrostedSheet
import app.morphe.manager.ui.oru.components.OruTapGate
import app.morphe.manager.ui.oru.components.OruGlassToggle
import app.morphe.manager.ui.oru.components.OruRowControl
import app.morphe.manager.ui.oru.components.OruSectionLabel
import app.morphe.manager.ui.oru.components.OruSettingsRow
import app.morphe.manager.ui.oru.components.OruSwapText
import app.morphe.manager.ui.oru.components.OruThemeToggle
import app.morphe.manager.ui.oru.components.oruCascade
import app.morphe.manager.ui.oru.components.oruSlab
import app.morphe.manager.ui.oru.theme.OruGroupShape
import app.morphe.manager.ui.oru.theme.OruMotion
import app.morphe.manager.ui.oru.theme.OruSpacing
import app.morphe.manager.ui.oru.theme.rememberOruReducedMotion
import app.morphe.manager.ui.screen.settings.appearance.LanguagePickerDialog
import app.morphe.manager.ui.screen.settings.system.AboutDialog
import app.morphe.manager.ui.screen.settings.system.StorageManagementDialog
import app.morphe.manager.util.AppLocale
import app.morphe.manager.util.formatBytes
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

/**
 * The oru Settings sheet: six groups, top to bottom (see [OruSettingsSection]), and the Advanced page pushed over
 * them inside the same sheet. Choices and switches report to [events]. Rows that open something else are handled here:
 * language, free up space and about open their dialog, and Advanced and Signing key open the Advanced page.
 *
 * [open] says what the sheet shows when it opens. It is taken each time the sheet opens, so a banner can point at a
 * group of the list, and Focus can open straight to the signing key. [onOpenPatchSources] is called when Advanced asks
 * for the source list: the host closes this sheet and opens that list on Home, so the two sheets are never stacked.
 */
@Composable
fun OruSettingsSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    state: OruSettingsUiState,
    events: OruSettingsEvents,
    onOpenPatchSources: () -> Unit,
    open: OruSettingsOpen = OruSettingsOpen(),
) {
    val context = LocalContext.current
    val language by AppLocale.selected.collectAsStateWithLifecycle()
    val reduced = rememberOruReducedMotion()
    var showLanguagePicker by rememberSaveable { mutableStateOf(false) }
    var showStorage by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }

    // The page the sheet is on, and the Advanced group it opens at. Both are taken from [open] each time the sheet opens,
    // so a page left open when the sheet closed does not come back the next time
    var page by rememberSaveable { mutableStateOf(OruSettingsPages.startPage(open)) }
    var advancedGroup by rememberSaveable { mutableStateOf(open.advancedGroup) }
    var wasVisible by remember { mutableStateOf(visible) }
    // The taps a double tap on the island sends as the sheet opens are not taken by its rows (see OruFrostedSheet)
    val settleGate = remember { OruTapGate() }
    // Taken in the composition that opens the sheet, not in an effect after it: the first frame already shows that page
    if (visible && !wasVisible) {
        page = OruSettingsPages.startPage(open)
        advancedGroup = open.advancedGroup
        settleGate.arm()
    }
    wasVisible = visible

    // The Install with list of Advanced, nested over this sheet
    var showInstaller by rememberSaveable { mutableStateOf(false) }

    // Whether Android shows Oru's notifications. It is read again on resume, since the permission can change in the
    // system settings that the notifications helper opens
    var notificationsAllowed by remember { mutableStateOf(hasNotificationPermission(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notificationsAllowed = hasNotificationPermission(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Turning on background updates asks for the notification permission first
    var pendingKeepUpdated by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsAllowed = granted
        if (pendingKeepUpdated) {
            pendingKeepUpdated = false
            // A refused permission leaves the switch off, since updates could not be shown anyway
            if (granted) events.onToggle(OruSettingsRowId.KEEP_APPS_UPDATED, true)
        }
    }

    fun activate(row: OruSettingsRowUi, on: Boolean) {
        if (row.id == OruSettingsRowId.KEEP_APPS_UPDATED && on && !hasNotificationPermission(context)) {
            pendingKeepUpdated = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            events.onToggle(row.id, on)
        }
    }

    fun openRow(row: OruSettingsRowUi) {
        when (row.id) {
            OruSettingsRowId.LANGUAGE -> {
                // Android 13 and later keeps the per-app language in the system settings
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                } else {
                    showLanguagePicker = true
                }
            }
            OruSettingsRowId.FREE_UP_SPACE -> showStorage = true
            OruSettingsRowId.ABOUT -> showAbout = true
            OruSettingsRowId.ADVANCED -> {
                advancedGroup = null
                page = OruSettingsPage.ADVANCED
            }
            // The signing key lives in the Backup group of Advanced, so the page opens at it
            OruSettingsRowId.SIGNING_KEY -> {
                advancedGroup = OruAdvancedGroup.BACKUP
                page = OruSettingsPage.ADVANCED
            }
            // Switches and choices change in place, so they are not opened
            else -> Unit
        }
    }

    OruFrostedSheet(visible = visible, onDismiss = onDismiss, settle = settleGate) {
        // Advanced has its own back: the chevron and the back gesture return to the list, and only the list dismisses
        BackHandler(enabled = OruSettingsPages.handlesBack(page)) {
            OruSettingsPages.back(page)?.let { page = it }
        }

        val advanced = page == OruSettingsPage.ADVANCED
        val backLabel = stringResource(R.string.back)
        SheetHeader(
            title = stringResource(if (advanced) R.string.oru_settings_advanced else R.string.oru_settings_title),
            advanced = advanced,
            backLabel = backLabel,
            onBack = { OruSettingsPages.back(page)?.let { page = it } },
        )

        val scroll = rememberScrollState()
        // Top of the Updates group in the list, in list coordinates. It is known once the group has been laid out.
        var updatesTop by remember { mutableIntStateOf(-1) }
        LaunchedEffect(visible, open.section) {
            if (visible && open.section == OruSettingsSection.UPDATES) {
                snapshotFlow { updatesTop }.first { it >= 0 }
                scroll.animateScrollTo(updatesTop)
            }
        }

        // The page takes the height left under the grab handle and the header, and each page scrolls inside it
        val slidePx = with(LocalDensity.current) { PageSlide.roundToPx() }
        AnimatedContent(
            targetState = page,
            modifier = Modifier.weight(1f, fill = false),
            transitionSpec = {
                pageTransition(
                    reduced = reduced,
                    forward = OruSettingsPages.isForward(initialState, targetState),
                    slidePx = slidePx,
                )
            },
            label = "oruSettingsPage",
        ) { current ->
            when (current) {
                OruSettingsPage.ROOT -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scroll)
                        .padding(start = 12.dp, end = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(OruSpacing.Large),
                ) {
                    state.sections.forEachIndexed { index, section ->
                        val groupModifier = if (section.section == OruSettingsSection.UPDATES) {
                            Modifier.onGloballyPositioned { coordinates ->
                                // The scroll offset is added back, so the value does not change while the list scrolls
                                updatesTop = (coordinates.positionInParent().y + scroll.value).roundToInt()
                            }
                        } else {
                            Modifier
                        }
                        SettingsSection(
                            section = section,
                            modifier = groupModifier.oruCascade(index),
                            events = events,
                            onActivate = { row, on -> activate(row, on) },
                            onOpen = { row -> openRow(row) },
                            notificationsOff = !notificationsAllowed,
                        )
                    }
                }
                OruSettingsPage.ADVANCED -> OruAdvancedContent(
                    focusGroup = advancedGroup,
                    onOpenSources = onOpenPatchSources,
                    onOpenInstaller = { showInstaller = true },
                )
            }
        }
    }

    // Drawn after the sheet, so it lies over it
    OruSettingsInstallerSheet(visible = showInstaller, onDismiss = { showInstaller = false })

    if (showLanguagePicker) {
        LanguagePickerDialog(
            currentLanguage = language,
            onLanguageSelected = { code ->
                events.onLanguageSelected(code)
                showLanguagePicker = false
            },
            onDismiss = { showLanguagePicker = false },
        )
    }

    if (showStorage) {
        StorageManagementDialog(onDismissRequest = { showStorage = false })
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

/**
 * The sheet's header: the back chevron (on the Advanced page only) and the page title, centred on both pages. Both ends
 * keep a 48 dp slot, so the title does not move when the chevron fades in. The title swaps with [OruSwapText]. The
 * header has the same height on both pages, so the body does not move when the page changes.
 */
@Composable
private fun SheetHeader(
    title: String,
    advanced: Boolean,
    backLabel: String,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeaderHeight),
    ) {
        AnimatedVisibility(
            visible = advanced,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp),
            enter = fadeIn(tween(OruMotion.ExpandFadeMillis)),
            exit = fadeOut(tween(OruMotion.ExpandFadeMillis)),
        ) {
            // A 48 dp touch target, the Material default for an icon button
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = backLabel,
                )
            }
        }
        OruSwapText(
            text = title,
            // The sheet title is 22/28 bold
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = HeaderSlot),
            contentAlignment = Alignment.Center,
        )
    }
}

/**
 * How the page changes: the new page fades in and slides [slidePx] from the side it comes from, and the old one fades
 * out and slides the other way. Reduced motion keeps the cross-fade only.
 */
private fun pageTransition(reduced: Boolean, forward: Boolean, slidePx: Int): ContentTransform {
    val fade = tween<Float>(OruMotion.ExpandFadeMillis)
    if (reduced) {
        return fadeIn(fade) togetherWith fadeOut(fade)
    }
    val direction = if (forward) 1 else -1
    return (fadeIn(fade) + slideInHorizontally(OruMotion.expand(reduced)) { direction * slidePx }) togetherWith
        (fadeOut(fade) + slideOutHorizontally(OruMotion.expand(reduced)) { -direction * slidePx })
}

/**
 * One group: a heading for the groups that have one, then the rows on one raised slab. The group is a traversal group
 * named for TalkBack, so its rows are read together and in order.
 */
@Composable
private fun SettingsSection(
    section: OruSettingsSectionUi,
    modifier: Modifier,
    events: OruSettingsEvents,
    onActivate: (OruSettingsRowUi, Boolean) -> Unit,
    onOpen: (OruSettingsRowUi) -> Unit,
    notificationsOff: Boolean,
) {
    val name = stringResource(sectionNameRes(section.section))
    val reduced = rememberOruReducedMotion()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = false) {
                isTraversalGroup = true
                contentDescription = name
            },
    ) {
        if (hasHeading(section.section)) {
            OruSectionLabel(text = name, modifier = SectionLabelModifier)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .oruSlab(
                    shape = OruGroupShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    overContent = true,
                ),
            verticalArrangement = Arrangement.spacedBy(OruSpacing.GroupGap),
        ) {
            section.rows.forEach { row ->
                // A revealed row (mobile data under Keep apps up to date) expands and collapses with the expand token
                AnimatedVisibility(
                    visible = row.shown,
                    enter = if (reduced) EnterTransition.None else
                        fadeIn(tween(OruMotion.ExpandFadeMillis)) + expandVertically(OruMotion.expand(reduced)),
                    exit = if (reduced) ExitTransition.None else
                        fadeOut(tween(OruMotion.ExpandFadeMillis)) + shrinkVertically(OruMotion.expand(reduced)),
                ) {
                    SettingsRowItem(
                        row = row,
                        events = events,
                        onToggle = { onActivate(row, it) },
                        onOpen = { onOpen(row) },
                        notificationsOff = notificationsOff,
                    )
                }
            }
        }
    }
}

/** One row of the sheet. Copy comes from oru_strings.xml, keyed by [OruSettingsRowUi.id]. */
@Composable
private fun SettingsRowItem(
    row: OruSettingsRowUi,
    events: OruSettingsEvents,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
    notificationsOff: Boolean,
) {
    val title = stringResource(titleRes(row.id))
    // Updates are on but Android will not show their notifications: the helper says so, and the action leads to the setting
    val notificationsBlocked = notificationsOff && row.id == OruSettingsRowId.KEEP_APPS_UPDATED &&
        (row.value as? OruSettingsValue.Toggle)?.on == true
    val supporting = if (notificationsBlocked) {
        stringResource(R.string.oru_settings_notifications_off)
    } else {
        supportingText(row)
    }

    when (val value = row.value) {
        is OruSettingsValue.Theme -> ChoiceRow(label = title) {
            OruThemeToggle(
                themeMode = value.mode,
                onThemeModeChange = { events.onThemeModeSelected(it) },
            )
        }
        is OruSettingsValue.Glass -> ChoiceRow(label = title) {
            OruGlassToggle(
                glassStyle = value.style,
                onGlassStyleChange = { events.onGlassStyleSelected(it) },
            )
        }
        is OruSettingsValue.Toggle -> Column(modifier = Modifier.fillMaxWidth()) {
            OruSettingsRow(
                title = title,
                supporting = supporting,
                control = OruRowControl.Switch(value.on),
                onClick = { onToggle(!value.on) },
            )
            if (notificationsBlocked) NotificationSettingsAction()
        }
        is OruSettingsValue.Text -> OruSettingsRow(
            title = title,
            supporting = supporting,
            // The About row shows the version as its value. Other text rows lead somewhere.
            control = if (row.id == OruSettingsRowId.ABOUT) OruRowControl.Value(value.text) else OruRowControl.Chevron,
            onClick = onOpen,
        )
        is OruSettingsValue.Bytes, OruSettingsValue.Link -> OruSettingsRow(
            title = title,
            supporting = supporting,
            control = OruRowControl.Chevron,
            onClick = onOpen,
        )
    }
}

/** Opens Oru's notification setting, under the updates row when Android will not show notifications for Oru. */
@Composable
private fun NotificationSettingsAction() {
    val context = LocalContext.current
    TextButton(
        onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        },
    ) {
        Text(text = stringResource(R.string.oru_action_open_settings))
    }
}

/** A row that holds a segmented choice: its label above the segments, with the same touch height as a row. */
@Composable
private fun ChoiceRow(label: String, choice: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ChoiceRowMinHeight)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(OruSpacing.Small),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        choice()
    }
}

private fun titleRes(id: OruSettingsRowId): Int = when (id) {
    OruSettingsRowId.THEME -> R.string.oru_quick_theme_group
    OruSettingsRowId.GLASS -> R.string.oru_quick_glass_group
    OruSettingsRowId.ANIMATIONS -> R.string.oru_settings_animations
    OruSettingsRowId.WALLPAPER_COLORS -> R.string.oru_settings_wallpaper
    OruSettingsRowId.LANGUAGE -> R.string.oru_settings_language
    OruSettingsRowId.KEEP_APPS_UPDATED -> R.string.oru_settings_updates
    OruSettingsRowId.MOBILE_DATA_UPDATES -> R.string.oru_settings_mobile_data
    OruSettingsRowId.FREE_UP_SPACE -> R.string.oru_settings_free_space
    OruSettingsRowId.SIGNING_KEY -> R.string.oru_settings_signing_key
    OruSettingsRowId.ABOUT -> R.string.oru_settings_about
    OruSettingsRowId.ADVANCED -> R.string.oru_settings_advanced
}

private fun sectionNameRes(section: OruSettingsSection): Int = when (section) {
    OruSettingsSection.APPEARANCE -> R.string.oru_settings_section_appearance
    OruSettingsSection.LANGUAGE -> R.string.oru_settings_language
    OruSettingsSection.UPDATES -> R.string.oru_settings_section_updates
    OruSettingsSection.STORAGE -> R.string.oru_settings_section_storage
    OruSettingsSection.ABOUT -> R.string.oru_settings_about
    OruSettingsSection.ADVANCED -> R.string.oru_settings_advanced
}

/** Groups of several rows carry a heading. A group of one row is named by its row, so it does not repeat it. */
private fun hasHeading(section: OruSettingsSection): Boolean = when (section) {
    OruSettingsSection.APPEARANCE, OruSettingsSection.UPDATES, OruSettingsSection.STORAGE -> true
    OruSettingsSection.LANGUAGE, OruSettingsSection.ABOUT, OruSettingsSection.ADVANCED -> false
}

@Composable
private fun supportingText(row: OruSettingsRowUi): String? {
    val context = LocalContext.current
    return when (val value = row.value) {
        is OruSettingsValue.Toggle -> helperRes(row.id)?.let { stringResource(it) }
        is OruSettingsValue.Text -> when (row.id) {
            OruSettingsRowId.LANGUAGE -> value.text.ifBlank { stringResource(R.string.oru_settings_language_phone) }
            else -> helperRes(row.id)?.let { stringResource(it) }
        }
        is OruSettingsValue.Bytes -> if (value.bytes > 0L) {
            stringResource(R.string.oru_settings_free_space_value, context.formatBytes(value.bytes))
        } else {
            stringResource(R.string.oru_settings_free_space_none)
        }
        OruSettingsValue.Link -> helperRes(row.id)?.let { stringResource(it) }
        is OruSettingsValue.Theme, is OruSettingsValue.Glass -> null
    }
}

private fun helperRes(id: OruSettingsRowId): Int? = when (id) {
    OruSettingsRowId.ANIMATIONS -> R.string.oru_settings_animations_helper
    OruSettingsRowId.WALLPAPER_COLORS -> R.string.oru_settings_wallpaper_helper
    OruSettingsRowId.KEEP_APPS_UPDATED -> R.string.oru_settings_updates_helper
    OruSettingsRowId.MOBILE_DATA_UPDATES -> R.string.oru_settings_mobile_data_helper
    OruSettingsRowId.FREE_UP_SPACE -> R.string.oru_settings_free_space_helper
    OruSettingsRowId.SIGNING_KEY -> R.string.oru_settings_signing_key_helper
    OruSettingsRowId.ABOUT -> R.string.oru_settings_about_helper
    OruSettingsRowId.ADVANCED -> R.string.oru_settings_advanced_helper
    else -> null
}

private val SectionLabelModifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
private val ChoiceRowMinHeight = 56.dp
private val HeaderHeight = 48.dp
// The 48 dp slot each end of the header keeps free, the size of the chevron's touch target
private val HeaderSlot = 48.dp
/** How far a page moves sideways as it comes in or goes out (screens move by a fraction of their size; this is the sheet's). */
private val PageSlide = 24.dp

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
