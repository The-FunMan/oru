/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OruExternalBatchGateTest {
    private val oru = FlavorExternalBatchGate(oruUx = true)
    private val morphe = FlavorExternalBatchGate(oruUx = false)

    private val caller = "com.example.automation"
    private val trusted = setOf(caller)

    @Test
    fun switchOffIgnoresEveryRequestInOru() {
        assertEquals(ExternalBatchVerdict.IGNORE, oru.verdict(false, caller, trusted))
        assertEquals(ExternalBatchVerdict.IGNORE, oru.verdict(false, caller, emptySet()))
        assertEquals(ExternalBatchVerdict.IGNORE, oru.verdict(false, null, emptySet()))
    }

    @Test
    fun switchOffIgnoresEveryRequestInMorphe() {
        assertEquals(ExternalBatchVerdict.IGNORE, morphe.verdict(false, caller, trusted))
        assertEquals(ExternalBatchVerdict.IGNORE, morphe.verdict(false, null, emptySet()))
    }

    @Test
    fun switchOnShowsConfirmForUnknownCallerInOru() {
        assertEquals(ExternalBatchVerdict.CONFIRM, oru.verdict(true, null, emptySet()))
        assertEquals(ExternalBatchVerdict.CONFIRM, oru.verdict(true, caller, emptySet()))
    }

    @Test
    fun oruIgnoresAllowlistForAutoApproval() {
        // A caller that was trusted before the change still gets the confirm
        assertEquals(ExternalBatchVerdict.CONFIRM, oru.verdict(true, caller, trusted))
    }

    @Test
    fun oruNeverTrusts() {
        assertFalse(oru.mayTrustCaller())
    }

    @Test
    fun morpheAutoApprovesAllowlistedCaller() {
        assertEquals(ExternalBatchVerdict.APPROVE, morphe.verdict(true, caller, trusted))
    }

    @Test
    fun morpheConfirmsUnknownOrUntrustedCaller() {
        assertEquals(ExternalBatchVerdict.CONFIRM, morphe.verdict(true, null, trusted))
        assertEquals(ExternalBatchVerdict.CONFIRM, morphe.verdict(true, caller, emptySet()))
        assertEquals(ExternalBatchVerdict.CONFIRM, morphe.verdict(true, "com.other", trusted))
    }

    @Test
    fun morpheMayTrustCaller() {
        assertTrue(morphe.mayTrustCaller())
    }
}
