/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OruSettingsBytesTest {

    @Test
    fun `nothing and a few leftover bytes count as nothing to remove`() {
        assertFalse(OruSettingsValue.Bytes(0L).removable)
        assertFalse(OruSettingsValue.Bytes(36L).removable)
        assertFalse(OruSettingsValue.Bytes(OruSettingsValue.Bytes.MIN_REMOVABLE_BYTES - 1).removable)
    }

    @Test
    fun `a kilobyte or more is worth a clean-up`() {
        assertTrue(OruSettingsValue.Bytes(OruSettingsValue.Bytes.MIN_REMOVABLE_BYTES).removable)
        assertTrue(OruSettingsValue.Bytes(1_200L).removable)
        assertTrue(OruSettingsValue.Bytes(250_000_000L).removable)
    }
}
