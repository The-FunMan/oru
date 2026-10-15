/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.install

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OruSignatureCheckTest {

    private class FakeSignatureSource(
        private val installed: Map<String, Set<String>> = emptyMap(),
        private val oruKey: Set<String> = emptySet()
    ) : SignatureSource {
        var keyReads = 0
            private set

        override fun isInstalled(packageName: String): Boolean = packageName in installed

        override fun installedSignatureHashes(packageName: String): Set<String> =
            installed[packageName].orEmpty()

        override suspend fun oruKeyHashes(): Set<String> {
            keyReads++
            return oruKey
        }
    }

    private val pkg = "com.example.app"
    private val stock = setOf("STOCK")
    private val oru = setOf("ORU")

    private fun check(source: SignatureSource): SignatureCheck =
        runBlocking { OruSignatureCheck(source).check(pkg) }

    @Test
    fun notInstalledIsNoConflictEvenWithoutKey() {
        val source = FakeSignatureSource(installed = emptyMap(), oruKey = oru)
        assertEquals(SignatureCheck.NoConflict, check(source))
    }

    @Test
    fun notInstalledDoesNotReadTheKey() {
        val source = FakeSignatureSource()
        check(source)
        assertEquals(0, source.keyReads)
    }

    @Test
    fun installedWithoutKeystoreIsUnknownNoKeystoreAndCarriesInstalledHashes() {
        val source = FakeSignatureSource(installed = mapOf(pkg to stock), oruKey = emptySet())
        val result = check(source)
        assertIs<SignatureCheck.UnknownNoKeystore>(result)
        assertEquals(stock, result.installedHashes)
        assertTrue(result.conflicts)
    }

    @Test
    fun installedWithDifferentKeyWillConflict() {
        val source = FakeSignatureSource(installed = mapOf(pkg to stock), oruKey = oru)
        val result = check(source)
        assertEquals(SignatureCheck.WillConflict(stock, oru), result)
        assertTrue(result.conflicts)
    }

    @Test
    fun installedWithSameKeyIsNoConflict() {
        val source = FakeSignatureSource(installed = mapOf(pkg to oru), oruKey = oru)
        assertEquals(SignatureCheck.NoConflict, check(source))
    }

    @Test
    fun rotatedCertificateHistoryMatchesWhenAnyFingerprintIsOru() {
        // The package manager reports the whole signing history; one shared fingerprint is enough
        val source = FakeSignatureSource(
            installed = mapOf(pkg to setOf("OLD", "ORU")),
            oruKey = setOf("ORU", "OTHER")
        )
        assertEquals(SignatureCheck.NoConflict, check(source))
    }

    @Test
    fun unreadableInstalledSignatureIsNotAConflict() {
        val source = FakeSignatureSource(installed = mapOf(pkg to emptySet()), oruKey = oru)
        val result = check(source)
        assertEquals(SignatureCheck.UnknownInstalledSignature, result)
        assertFalse(result.conflicts)
    }

    @Test
    fun noConflictDoesNotCountAsConflict() {
        assertFalse(SignatureCheck.NoConflict.conflicts)
        assertFalse(SignatureCheck.UnknownInstalledSignature.conflicts)
    }

    @Test
    fun classifyCoversEveryBranchWithoutAndroid() {
        assertEquals(SignatureCheck.UnknownInstalledSignature, OruSignatureCheck.classify(emptySet(), oru))
        assertEquals(
            SignatureCheck.UnknownNoKeystore(stock),
            OruSignatureCheck.classify(stock, emptySet())
        )
        assertEquals(SignatureCheck.NoConflict, OruSignatureCheck.classify(oru, oru))
        assertEquals(SignatureCheck.WillConflict(stock, oru), OruSignatureCheck.classify(stock, oru))
    }
}
