/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus.expert

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.morphe.manager.ui.oru.components.OruFrostedSheet
import app.morphe.manager.ui.oru.components.oruInk
import app.morphe.manager.ui.oru.focus.OruFocusViewModel
import app.morphe.manager.ui.oru.focus.customize.OruCustomizeContent
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.viewmodel.HomeViewModel

/**
 * The sheets of the Focus. [Setup] is the one the summary line opens; the others are its rows. [PatchOptions] and
 * [Customize] open from the patch selection.
 */
sealed interface OruExpertSheet {
    data object Setup : OruExpertSheet
    data object Version : OruExpertSheet
    data object Installer : OruExpertSheet
    data object Sources : OruExpertSheet
    data object Patches : OruExpertSheet
    data class PatchOptions(val bundleUid: Int, val patchName: String) : OruExpertSheet
    data object Customize : OruExpertSheet
}

/**
 * Which sheet of the Focus is open. The rows open sheets here, and [OruExpertSheets] draws them over the Focus.
 * [shown] keeps the last sheet while the open one animates away.
 */
class OruExpertSheetHost {
    var sheet: OruExpertSheet? by mutableStateOf(null)
        private set

    var shown: OruExpertSheet? by mutableStateOf(null)
        private set

    /** The sheet [close] brings back, for a sheet opened from another one (the rows of the Setup sheet). */
    private var returnTo: OruExpertSheet? = null

    /** The sheet the patch selection opens with. Set before the selection is reopened, taken when it opens. */
    private var pending: OruExpertSheet? = null

    /** Opens [next]. [returnTo] is the sheet that [close] shows again, when [next] was opened from it. */
    fun open(next: OruExpertSheet, returnTo: OruExpertSheet? = null) {
        this.returnTo = returnTo
        shown = next
        sheet = next
    }

    /** Closes the open sheet, or shows the one it was opened from. */
    fun close() {
        val back = returnTo
        returnTo = null
        if (back != null) {
            shown = back
            sheet = back
        } else {
            sheet = null
        }
    }

    /** Closes every sheet, whatever it was opened from. */
    fun closeAll() {
        returnTo = null
        sheet = null
    }

    /** Opens [next] when the patch selection opens next, which the rows that reopen it need. */
    fun openWhenSelecting(next: OruExpertSheet) {
        pending = next
    }

    internal fun takePending(): OruExpertSheet? = pending.also { pending = null }
}

/** The host the Focus route provides to its rows. Null outside the route, where the rows open nothing. */
val LocalOruExpertSheets = staticCompositionLocalOf<OruExpertSheetHost?> { null }

/**
 * Draws the open sheet over the Focus and follows the patch selection. The selection is the existing
 * HomeViewModel state (`showExpertModeDialog`): while it is open the patch sheet is shown, in simple mode too,
 * because the flow has no other answer for a selection it cannot make by itself. A selection that already has
 * patches and was not asked for by the user is answered with them, without the sheet.
 */
@Composable
internal fun OruExpertSheets(
    host: OruExpertSheetHost,
    vm: OruFocusViewModel,
    home: HomeViewModel,
    item: HomeAppItem,
    /** The APK choice is showing, so the version can be changed. */
    choosing: Boolean,
) {
    // A selection answered with its defaults never shows the sheet (see OruFocusViewModel.autoSelecting)
    val selecting = home.showExpertModeDialog && !vm.autoSelecting
    LaunchedEffect(selecting) {
        if (selecting) {
            host.open(host.takePending() ?: OruExpertSheet.Patches)
        } else {
            host.closeAll()
        }
    }

    val current = host.shown ?: return
    OruFrostedSheet(
        visible = host.sheet != null,
        onDismiss = {
            when (current) {
                OruExpertSheet.Patches -> home.cleanupExpertModeData()
                is OruExpertSheet.PatchOptions, OruExpertSheet.Customize -> host.open(OruExpertSheet.Patches)
                else -> host.close()
            }
        },
    ) {
        when (current) {
            OruExpertSheet.Patches -> OruPatchContent(host = host, home = home, item = item)
            is OruExpertSheet.PatchOptions -> OruOptionsContent(
                host = host,
                home = home,
                bundleUid = current.bundleUid,
                patchName = current.patchName,
            )
            OruExpertSheet.Customize -> OruCustomizeContent(host = host, home = home)
            OruExpertSheet.Setup -> OruSetupContent(host = host, vm = vm, home = home, item = item, choosing = choosing)
            OruExpertSheet.Version -> OruVersionContent(host = host, vm = vm, home = home)
            OruExpertSheet.Installer -> OruInstallerContent(host = host, vm = vm)
            OruExpertSheet.Sources -> OruSourcesContent(host = host, vm = vm, home = home, item = item)
        }
    }
}

/** The mark a chosen row carries, or null for a row that is not the choice. */
internal fun oruChosenMark(chosen: Boolean): (@Composable () -> Unit)? = if (chosen) ChosenMark else null

private val ChosenMark: @Composable () -> Unit = {
    Icon(
        imageVector = Icons.Outlined.Check,
        contentDescription = null,
        tint = oruInk(),
    )
}
