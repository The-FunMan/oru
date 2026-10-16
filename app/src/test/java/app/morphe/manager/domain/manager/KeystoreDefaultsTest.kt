/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.manager

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The preference defaults are what an existing keystore resolves to when nothing was stored with it,
 * so they must stay on the names those keystores were made with.
 */
class KeystoreDefaultsTest {
    @Test
    fun legacyDefaultStaysForExistingKeystores() {
        assertEquals("Morphe", KeystoreManager.DEFAULT)
    }

    @Test
    fun oruNewKeystoreDiffersFromLegacyDefault() {
        assertNotEquals(KeystoreManager.DEFAULT, OruEndpoints(upstreamServices = false).newKeystoreAlias)
    }
}
