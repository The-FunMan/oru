/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.install

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.ui.oru.components.OruDecisionDialog
import app.morphe.manager.ui.oru.components.OruDialogAction
import app.morphe.manager.ui.oru.components.OruTone

/**
 * The signing key notice, shown once on the Done of the first patch (see [OruDecisionCopy.keyNotice]). Its action opens
 * the signing key settings; Not now keeps the install where it is. Both close the notice for good.
 *
 * @param onOpen opens the signing key settings
 * @param onDismiss closes the notice
 */
@Composable
fun OruKeyNoticeDialog(
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val copy = OruDecisionCopy.keyNotice()
    OruDecisionDialog(
        title = copy.title?.let { stringResource(it) }.orEmpty(),
        body = copy.body?.let { stringResource(it) }.orEmpty(),
        primary = OruDialogAction(
            label = copy.primaryAction?.let { stringResource(it) }.orEmpty(),
            onClick = onOpen,
        ),
        secondary = OruDialogAction(
            label = stringResource(R.string.oru_action_not_now),
            onClick = onDismiss,
        ),
        tone = OruTone.Warn,
        onDismiss = onDismiss,
    )
}
