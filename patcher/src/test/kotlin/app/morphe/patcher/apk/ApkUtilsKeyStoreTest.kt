/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.apk

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

internal class ApkUtilsKeyStoreTest {
    @TempDir
    lateinit var temporaryDirectory: File

    private fun details(file: File) = ApkUtils.KeyStoreDetails(file, "store-pass", "alias", "key-pass")

    @Test
    fun `default certificate validity is eight years and fits the X509 time range`() {
        val eightYears = (365.days * 8).inWholeMilliseconds
        val validUntil = ApkUtils.PrivateKeyCertificatePairDetails().validUntil.time
        val expected = System.currentTimeMillis() + eightYears

        assertTrue(validUntil in (expected - 60_000)..expected)
        assertTrue(Instant.ofEpochMilli(validUntil).atZone(ZoneOffset.UTC).year <= 2049)

        val details = ApkUtils.PrivateKeyCertificatePairDetails()
        val certificate = ApkSigner.newPrivateKeyCertificatePair(details.commonName, details.validUntil).certificate
        assertEquals(details.validUntil.time / 1000, certificate.notAfter.time / 1000)
    }

    @Test
    fun `new keystore is written complete and leaves no staging file behind`() {
        val keyStoreFile = temporaryDirectory.resolve("test.keystore")
        val details = details(keyStoreFile)

        ApkUtils.newPrivateKeyCertificatePair(ApkUtils.PrivateKeyCertificatePairDetails(), details)

        assertTrue(keyStoreFile.length() > 0)
        assertFalse(temporaryDirectory.resolve("test.keystore.tmp").exists())
        assertNotNull(ApkUtils.readPrivateKeyCertificatePairFromKeyStore(details))
    }

    @Test
    fun `existing keystore is replaced as a whole`() {
        val keyStoreFile = temporaryDirectory.resolve("test.keystore").apply { writeText("old") }
        val details = details(keyStoreFile)

        ApkUtils.newPrivateKeyCertificatePair(ApkUtils.PrivateKeyCertificatePairDetails(), details)

        assertEquals(listOf("test.keystore"), temporaryDirectory.list()!!.toList())
        assertNotNull(ApkUtils.readPrivateKeyCertificatePairFromKeyStore(details))
    }
}
