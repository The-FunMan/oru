/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.manager

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OruEndpointsTest {
    @Test
    fun oruFlavourHidesUnavatar() {
        assertFalse(OruEndpoints(upstreamServices = false).unavatarEnabled)
    }

    @Test
    fun morpheFlavourKeepsUnavatar() {
        assertTrue(OruEndpoints(upstreamServices = true).unavatarEnabled)
    }

    @Test
    fun oruRunsUpdateWorkerOnGmsDevices() {
        val oru = OruEndpoints(upstreamServices = false)

        assertTrue(oru.updateWorkerScheduled(notificationsEnabled = true, hasGms = true))
        assertTrue(oru.updateWorkerScheduled(notificationsEnabled = true, hasGms = false))
        assertFalse(oru.updateWorkerScheduled(notificationsEnabled = false, hasGms = true))
        assertFalse(oru.updateWorkerScheduled(notificationsEnabled = false, hasGms = false))
    }

    @Test
    fun morpheLeavesGmsDevicesToFcm() {
        val morphe = OruEndpoints(upstreamServices = true)

        assertFalse(morphe.updateWorkerScheduled(notificationsEnabled = true, hasGms = true))
        assertTrue(morphe.updateWorkerScheduled(notificationsEnabled = true, hasGms = false))
        assertFalse(morphe.updateWorkerScheduled(notificationsEnabled = false, hasGms = false))
    }

    @Test
    fun oruCreatesNewKeystoresAsOru() {
        assertEquals("oru", OruEndpoints(upstreamServices = false).newKeystoreAlias)
    }

    @Test
    fun morpheKeepsStoredKeystoreCredentials() {
        assertNull(OruEndpoints(upstreamServices = true).newKeystoreAlias)
    }

    @Test
    fun oruNeverUsesMorpheApi() {
        assertFalse(OruEndpoints(upstreamServices = false).projectApiEnabled)
    }

    @Test
    fun morpheFlavourKeepsMorpheApi() {
        assertTrue(OruEndpoints(upstreamServices = true).projectApiEnabled)
    }

    @Test
    fun currentIsOru() {
        assertFalse(OruEndpoints.current.unavatarEnabled)
        assertFalse(OruEndpoints.current.projectApiEnabled)
        assertEquals("oru", OruEndpoints.current.newKeystoreAlias)
    }
}
