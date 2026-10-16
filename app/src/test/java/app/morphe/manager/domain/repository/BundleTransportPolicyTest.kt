/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.repository

import app.morphe.manager.network.service.HttpService
import app.morphe.manager.ui.viewmodel.BundleSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BundleTransportPolicyTest {
    @Test
    fun bareHostGetsHttpsScheme() {
        assertEquals(
            "https://example.com/patches.json",
            PatchBundleRepository.withHttpsScheme("example.com/patches.json")
        )
    }

    @Test
    fun httpsUrlIsKept() {
        assertEquals(
            "https://example.com/patches.json",
            PatchBundleRepository.withHttpsScheme("  https://example.com/patches.json ")
        )
    }

    @Test
    fun httpUrlIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            PatchBundleRepository.withHttpsScheme("http://example.com/patches.json")
        }
        assertFailsWith<IllegalArgumentException> {
            PatchBundleRepository.withHttpsScheme("HTTP://example.com/patches.json")
        }
    }

    @Test
    fun otherSchemesAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            PatchBundleRepository.withHttpsScheme("ftp://example.com/patches.json")
        }
    }

    @Test
    fun unparseableSourcesAreReportedWithReason() {
        val snapshots = listOf(
            snapshot("Good", "https://example.com/patches.json"),
            snapshot("Cleartext", "http://example.com/patches.json"),
        )

        val reasons = PatchBundleRepository.rejectedSourceReasons(snapshots, PatchBundleRepository.Companion::withHttpsScheme)

        assertEquals(1, reasons.size)
        assertTrue(reasons.single().startsWith("Cleartext: "), reasons.single())
        assertTrue(reasons.single().contains("https"), reasons.single())
    }

    @Test
    fun redirectLocationToHttpIsRefusedForHttpsRequest() {
        assertEquals(
            null,
            HttpService.resolveRedirectLocation("https://example.com/a", "http://example.com/b")
        )
    }

    @Test
    fun absoluteHttpsLocationIsKept() {
        assertEquals(
            "https://cdn.example.com/b",
            HttpService.resolveRedirectLocation("https://example.com/a", "https://cdn.example.com/b")
        )
    }

    @Test
    fun relativeLocationKeepsSchemeHostAndPort() {
        assertEquals(
            "https://example.com:8443/b",
            HttpService.resolveRedirectLocation("https://example.com:8443/a", "/b")
        )
        assertEquals(
            "http://127.0.0.1:9000/b",
            HttpService.resolveRedirectLocation("http://127.0.0.1:9000/a", "/b")
        )
    }

    private fun snapshot(name: String, source: String) = BundleSnapshot(
        name = name,
        source = source,
        autoUpdate = false,
        sortOrder = 0,
    )
}
