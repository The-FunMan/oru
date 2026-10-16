/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus.expert

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.oru.home.oruAppName
import app.morphe.manager.ui.oru.focus.OruFocusViewModel
import app.morphe.manager.ui.oru.sources.OruSourceChoiceContent
import app.morphe.manager.ui.oru.sources.rememberOruAppSources
import app.morphe.manager.ui.viewmodel.HomeViewModel

/**
 * The Source sheet of the Expert Focus: one source for this app, chosen from the sources that can patch it. It is the
 * same single choice as the Simple Focus and the Home card. Picking a source remembers it for the app and the Focus
 * follows it, so no other source's patches or versions are ever mixed in.
 */
@Composable
internal fun ColumnScope.OruSourcesContent(
    host: OruExpertSheetHost,
    vm: OruFocusViewModel,
    home: HomeViewModel,
    item: HomeAppItem,
) {
    val sources = rememberOruAppSources(home, item.packageName)
    OruSourceChoiceContent(
        sources = sources,
        appName = oruAppName(item.packageName, item.displayName),
        onPick = { uid ->
            vm.changeSource(item, uid)
            host.close()
        },
        onClose = { host.close() },
    )
}
