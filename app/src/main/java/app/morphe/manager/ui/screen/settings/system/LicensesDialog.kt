/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.DialogPadding
import app.morphe.manager.ui.screen.shared.ListScrollbar
import app.morphe.manager.ui.screen.shared.LocalDialogHorizontalInset
import app.morphe.manager.ui.screen.shared.dialogTextColor
import app.morphe.manager.ui.screen.shared.AppDialog
import app.morphe.manager.ui.screen.shared.AppDialogOutlinedButton
import app.morphe.manager.ui.screen.shared.ScrollToTopButton
import app.morphe.manager.ui.screen.shared.verticalScrollFade
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.LibraryColors
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.chipColors
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors
import com.mikepenz.aboutlibraries.ui.compose.util.strippedLicenseContent
import com.mikepenz.aboutlibraries.ui.compose.variant.LibraryActionKind

private const val NOTICE_UNIQUE_ID = "app.morphe.manager"
private val urlRegex = Regex("(https?://[\\w./?=&%-]+)")
// Copied verbatim from NOTICE in project root. Update manually if NOTICE changes.
private const val NOTICE_TEXT = """
Morphe NOTICE

https://github.com/MorpheApp/morphe-manager

=============

Portions of this software are provided "AS IS" by the Morphe software project.
Any express or implied warranties, including the implied warranties of
merchantability and fitness for a particular purpose, are disclaimed.


GPLv3 Section 7c: Prohibiting Misrepresentation of Origin

You are prohibited from misrepresenting the origin of the Program,
and modified versions of the Program must be identified and marked in
reasonable ways as different from the original version so as not to cause
confusion regarding their origin.


GPLv3 Section 7e: Declining Grant of Trademark Rights

This License does not grant any rights or permission under trademark law
to use the name "Morphe" or any of the Program's trade names, trademarks,
service marks, or logos.


=============

Oru

Oru is a modified version of Morphe Manager (https://github.com/MorpheApp/morphe-manager),
distributed under the GNU General Public License version 3 together with the additional
terms above under GPLv3 Section 7. The modifications are by The-FunMan and the contributors
to the Oru project (https://github.com/The-FunMan/oru).

Oru is an independent project. It is not made by, endorsed by, supported by or affiliated
with Morphe, MorpheApp, Google, YouTube, Reddit or any other company whose app names appear
in the project. Those names are used only to identify the apps that a patch targets.

The name "Oru" and the Oru mark are not licensed for use by other projects.


Third-party notices

The following components are included in Oru or in its build. The licences of the Gradle
dependencies are listed with their full text on the licences screen of the app
(About, then Credits, then Open-source licenses).

- Patcher: the files ApkMerger.kt, ApkEditorUtil.kt and AndroidManifestHelper.kt in
  patcher/src/main/kotlin/app/morphe/patcher/apk/ are adapted from REAndroid APKEditor
  (Copyright (C) 2022 github.com/REAndroid), licensed under the Apache License 2.0.
  The patcher in patcher/ keeps its own LICENSE and NOTICE files.
- ARSCLib (https://github.com/REAndroid/ARSCLib, MorpheApp fork), Apache License 2.0.
- apksig and apkzlib (The Android Open Source Project), Apache License 2.0.
- smali, baksmali and dexlib2 (MorpheApp fork of https://github.com/JesusFreke/smali),
  BSD 3-Clause License.
- Bouncy Castle (bcprov, bcpkix and bcpg, The Legion of the Bouncy Castle Inc.), MIT-style licence.
- Shizuku API and provider (RikkaApps), MIT License; HiddenApi stub and refine annotation
  (RikkaW), MIT License.
- Coil (coil-kt), Koin (insert-koin), Ktor and the kotlinx libraries (JetBrains), appiconloader
  (Zhang Hai), aboutlibraries (Mike Penz), kotlin-process (pgreze) and libsu (John Wu):
  Apache License 2.0.
- ComposeFadingEdges (GIGAMOLE), MIT License.
- Font Awesome Free icons (Fonticons, Inc., https://fontawesome.com), used through the
  compose-icons font-awesome library (BenjaminHalko, MIT License). The Font Awesome Free
  icons are licensed under CC BY 4.0.
- Material icons and Material Symbols (Google), Apache License 2.0. The shortcut icons
  app/src/main/res/drawable/ic_shortcut_repatch.xml and ic_shortcut_updates.xml come from
  https://github.com/google/material-design-icons.
- Yuji Boku typeface (Copyright 2021 The Yuji Project Authors, https://github.com/Kinutafontfactory/Yuji),
  SIL Open Font License 1.1. The wordmark uses app/src/main/res/font/yuji_boku_wordmark.ttf,
  a subset of the typeface that holds only the letters the wordmark needs. The file keeps the
  font's name and copyright notice.
- Notification sounds app/src/main/res/raw/success.ogg and error.ogg: their origin and
  licence are not recorded in this repository.
- Android and Google libraries used by the app (AndroidX, Jetpack Compose, Room, WorkManager,
  DataStore, Navigation, the splash screen, Firebase Cloud Messaging and Google Play services
  base). The AndroidX and Firebase libraries are licensed under the Apache License 2.0. The
  Google Play services library is distributed under Google's own terms. The Oru build has no
  Firebase configuration and removes the messaging service.
- Rust crates in native/ and native-packager/. Their versions are pinned in Cargo.lock, and
  each crate's licence is in its package metadata. The licences are not listed here.
- Oru's build replaces the Morphe logo artwork in app/src/main/res/drawable (files that state
  "Copyright 2025 Morphe ... not licensed under open source terms") with the Oru artwork in
  app/src/oru/res/drawable.

Patch bundles are not part of this repository. Each bundle is made and licensed by its
authors.
"""

/**
 * Licenses dialog.
 * Shows open-source library licenses via aboutlibraries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesDialog(onDismiss: () -> Unit) {
    AppDialog(
        title = stringResource(R.string.opensource_licenses),
        onDismissRequest = onDismiss,
        scrollable = false, // LibrariesContainer has its own LazyColumn
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        },
        padding = DialogPadding.Compact
    ) {
        // Libraries list
        val lazyListState = rememberLazyListState()
        val libraries by produceLibraries(R.raw.aboutlibraries)

        var openDialog by remember { mutableStateOf<Library?>(null) }
        var openSheet by remember { mutableStateOf<Library?>(null) }

        val chipColors = LibraryDefaults.chipColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
        val colors = LibraryDefaults.libraryColors(
            libraryBackgroundColor = MaterialTheme.colorScheme.background,
            libraryContentColor = dialogTextColor(),
            versionChipColors = chipColors,
            licenseChipColors = chipColors,
            fundingChipColors = chipColors,
        )

        Box(modifier = Modifier.weight(1f)) {
            LibrariesContainer(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScrollFade(lazyListState),
                libraries = libraries,
                dialogLibrary = openDialog,
                sheetLibrary = openSheet,
                onDialogLibraryChange = { openDialog = it },
                onSheetLibraryChange = { openSheet = it },
                lazyListState = lazyListState,
                colors = colors,
                onActionClick = { library, kind ->
                    if (kind == LibraryActionKind.License) {
                        openDialog = library
                        true
                    } else false
                },
                licenseDialogBody = { library, modifier ->
                    if (library.uniqueId == NOTICE_UNIQUE_ID) {
                        AutoLinkText(
                            text = NOTICE_TEXT,
                            modifier = modifier,
                            color = colors.dialogContentColor
                        )
                    } else {
                        ClickableLicenseDialogBody(
                            library = library,
                            colors = colors,
                            modifier = modifier
                        )
                    }
                }
            )

            ListScrollbar(
                listState = lazyListState,
                modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
            )

            ScrollToTopButton(
                listState = lazyListState,
                modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
            )
        }
    }
}

private val Library.linkifiedLicensesBody: String
    get() {
        val header = website?.let { "$it\n\n" }.orEmpty()

        val body = licenses.joinToString(separator = "\n\n\n\n") { license ->
            buildString {
                license.url?.let { append(it).append("\n\n") }
                license.strippedLicenseContent?.let { append(it) }
            }
        }

        return header + body
    }

@Composable
private fun ClickableLicenseDialogBody(
    library: Library,
    colors: LibraryColors,
    modifier: Modifier,
) {
    AutoLinkText(
        text = library.linkifiedLicensesBody,
        modifier = modifier,
        color = colors.dialogContentColor
    )
}

@Composable
private fun AutoLinkText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val linkColor = MaterialTheme.colorScheme.primary

    val annotated = remember(text, linkColor) { buildAnnotatedString {
        var lastIndex = 0
        val linkStyles = TextLinkStyles(style = SpanStyle(color = linkColor))

        urlRegex.findAll(text).forEach { match ->
            val url = match.value

            // Add text before the URL
            append(text.substring(lastIndex, match.range.first))

            // Add the URL as a link the text opens itself when tapped
            withLink(LinkAnnotation.Url(url, linkStyles)) {
                append(url)
            }

            lastIndex = match.range.last + 1
        }

        // Add remaining text
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    } }
    Text(
        text = annotated,
        modifier = modifier,
        style = LocalTextStyle.current.copy(color = color)
    )
}
