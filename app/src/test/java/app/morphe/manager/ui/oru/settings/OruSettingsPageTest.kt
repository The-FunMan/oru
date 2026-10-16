/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OruSettingsPageTest {

    @Test
    fun backFromAdvancedLeadsToRoot() {
        assertEquals(OruSettingsPage.ROOT, OruSettingsPages.back(OruSettingsPage.ADVANCED))
    }

    @Test
    fun rootHasNoBackSoItDismissesTheSheet() {
        assertNull(OruSettingsPages.back(OruSettingsPage.ROOT))
    }

    @Test
    fun onlyAdvancedTakesTheBackGesture() {
        assertTrue(OruSettingsPages.handlesBack(OruSettingsPage.ADVANCED))
        assertFalse(OruSettingsPages.handlesBack(OruSettingsPage.ROOT))
    }

    @Test
    fun pushingAdvancedMovesForwardAndReturningMovesBack() {
        assertTrue(OruSettingsPages.isForward(OruSettingsPage.ROOT, OruSettingsPage.ADVANCED))
        assertFalse(OruSettingsPages.isForward(OruSettingsPage.ADVANCED, OruSettingsPage.ROOT))
    }

    @Test
    fun aSheetOpensOnTheListUnlessAskedForAdvanced() {
        assertEquals(OruSettingsPage.ROOT, OruSettingsPages.startPage(OruSettingsOpen()))
        assertEquals(
            OruSettingsPage.ADVANCED,
            OruSettingsPages.startPage(OruSettingsOpen(page = OruSettingsPage.ADVANCED)),
        )
    }

    @Test
    fun theDefaultOpenIsTheTopOfTheList() {
        val open = OruSettingsOpen()

        assertEquals(OruSettingsPage.ROOT, open.page)
        assertNull(open.section)
        assertNull(open.advancedGroup)
    }

    @Test
    fun theSigningKeyLinkOpensTheBackupGroup() {
        val open = OruSettingsOpen(page = OruSettingsPage.ADVANCED, advancedGroup = OruAdvancedGroup.BACKUP)

        assertEquals(OruSettingsPage.ADVANCED, OruSettingsPages.startPage(open))
        assertEquals(OruAdvancedGroup.BACKUP, open.advancedGroup)
    }
}
