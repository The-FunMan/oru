/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.util

import app.morphe.manager.ui.screen.shared.resolveAllowedExtensions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FilePickerDecisionTest {

    @Test
    fun oruApkRequestOpensTheInAppPickerWhateverIsStored() {
        assertEquals(
            PickerKind.InApp,
            pickerKind(preferInAppPicker = true, customPickerPref = false, isTv = false, oruUx = true),
        )
        assertEquals(
            PickerKind.InApp,
            pickerKind(preferInAppPicker = true, customPickerPref = true, isTv = false, oruUx = true),
        )
    }

    @Test
    fun oruOtherRequestsStayOnTheSystemPickerWhateverIsStored() {
        assertEquals(
            PickerKind.System,
            pickerKind(preferInAppPicker = false, customPickerPref = true, isTv = false, oruUx = true),
        )
        assertEquals(
            PickerKind.System,
            pickerKind(preferInAppPicker = false, customPickerPref = false, isTv = false, oruUx = true),
        )
    }

    @Test
    fun morpheFollowsTheStoredChoiceUnlessAskedForTheInAppPicker() {
        assertEquals(
            PickerKind.InApp,
            pickerKind(preferInAppPicker = false, customPickerPref = true, isTv = false, oruUx = false),
        )
        assertEquals(
            PickerKind.System,
            pickerKind(preferInAppPicker = false, customPickerPref = false, isTv = false, oruUx = false),
        )
        assertEquals(
            PickerKind.InApp,
            pickerKind(preferInAppPicker = true, customPickerPref = false, isTv = false, oruUx = false),
        )
    }

    @Test
    fun tvAlwaysGetsTheInAppPicker() {
        assertEquals(
            PickerKind.InApp,
            pickerKind(preferInAppPicker = false, customPickerPref = false, isTv = true, oruUx = false),
        )
        assertEquals(
            PickerKind.InApp,
            pickerKind(preferInAppPicker = false, customPickerPref = false, isTv = true, oruUx = true),
        )
    }

    @Test
    fun refusedAccessFallsBackToTheSystemPickerOnlyForAnInAppRequest() {
        assertEquals(PickerKind.InApp, pickerAfterAccess(granted = true, preferInAppPicker = true))
        assertEquals(PickerKind.InApp, pickerAfterAccess(granted = true, preferInAppPicker = false))
        assertEquals(PickerKind.System, pickerAfterAccess(granted = false, preferInAppPicker = true))
        assertNull(pickerAfterAccess(granted = false, preferInAppPicker = false))
    }

    @Test
    fun apkMimeTypesNarrowTheInAppPickerToTheApkFamily() {
        assertEquals(setOf("apk", "apks", "xapk", "apkm"), resolveAllowedExtensions(APK_FILE_MIME_TYPES))
    }
}
