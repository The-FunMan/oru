/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.install

import android.app.Application
import android.content.Context
import app.morphe.manager.domain.manager.KeystoreManager
import app.morphe.manager.util.PM
import java.io.File

/**
 * Whether the installed app can be updated in place by a patched build signed with the Oru key.
 * This is about the certificate (C7 in the implementation plan), not the APK's own signature.
 */
sealed interface SignatureCheck {
    /**
     * Nothing to conflict with, or the installed certificate shares a fingerprint with the Oru
     * key (certificate rotation keeps the old fingerprints in the package manager's history).
     */
    data object NoConflict : SignatureCheck

    /**
     * The app is installed and no certificate it carries matches the Oru key. Android refuses
     * an in-place update, so the install needs the installed app removed first.
     */
    data class WillConflict(
        val installedHashes: Set<String>,
        val oruHashes: Set<String>
    ) : SignatureCheck

    /**
     * The app is installed but there is no readable Oru key yet (no keystore, or it cannot be
     * read). The first patch creates a new key, and a new key cannot match a signer already on the
     * phone, so a conflict is expected even though the Oru fingerprint is not known.
     */
    data class UnknownNoKeystore(val installedHashes: Set<String>) : SignatureCheck

    /**
     * The app is installed but its certificates cannot be read. Treated as no conflict, the same
     * as [app.morphe.manager.util.PM.hasSignatureMismatch] does; the install-time check still runs.
     */
    data object UnknownInstalledSignature : SignatureCheck
}

/** True when the result means Android will refuse an in-place update. */
val SignatureCheck.conflicts: Boolean
    get() = this is SignatureCheck.WillConflict || this is SignatureCheck.UnknownNoKeystore

/** Reads what [OruSignatureCheck] needs. Implemented over PM and KeystoreManager; faked in tests. */
interface SignatureSource {
    /** Whether a package with [packageName] is installed. */
    fun isInstalled(packageName: String): Boolean

    /** SHA-256 certificate fingerprints of the installed [packageName]; empty when unreadable. */
    fun installedSignatureHashes(packageName: String): Set<String>

    /** SHA-256 fingerprints of every certificate in the Oru keystore; empty when none or unreadable. */
    suspend fun oruKeyHashes(): Set<String>
}

/**
 * Compares the installed app with the Oru signing key before any patching happens, when there is
 * no output APK to check yet. Pure logic: all Android reads go through [SignatureSource].
 */
class OruSignatureCheck(private val source: SignatureSource) {

    suspend fun check(packageName: String): SignatureCheck {
        // No installed app means no conflict, whatever key the patch will use
        if (!source.isInstalled(packageName)) return SignatureCheck.NoConflict

        val installed = source.installedSignatureHashes(packageName)
        if (installed.isEmpty()) return SignatureCheck.UnknownInstalledSignature

        val oru = source.oruKeyHashes()
        return classify(installed, oru)
    }

    companion object {
        /** Pure decision over two fingerprint sets, exposed for tests and for the install-time check. */
        fun classify(installedHashes: Set<String>, oruHashes: Set<String>): SignatureCheck = when {
            installedHashes.isEmpty() -> SignatureCheck.UnknownInstalledSignature
            oruHashes.isEmpty() -> SignatureCheck.UnknownNoKeystore(installedHashes)
            installedHashes.any { it in oruHashes } -> SignatureCheck.NoConflict
            else -> SignatureCheck.WillConflict(installedHashes, oruHashes)
        }
    }
}

/**
 * [SignatureSource] over the app's own PM and KeystoreManager.
 *
 * Keystore presence is read from the file directly, because [KeystoreManager.signingKeyInfo] returns
 * null both for a missing file and for an unreadable one. The path matches KeystoreManager's own.
 */
class AppSignatureSource(
    private val app: Application,
    private val pm: PM,
    private val keystore: KeystoreManager
) : SignatureSource {

    override fun isInstalled(packageName: String): Boolean = pm.getPackageInfo(packageName) != null

    override fun installedSignatureHashes(packageName: String): Set<String> =
        pm.getInstalledSignatureHashes(packageName)

    override suspend fun oruKeyHashes(): Set<String> {
        if (!keystoreFile().exists()) return emptySet()
        return keystore.signingCertificateHashes()
    }

    private fun keystoreFile(): File =
        app.getDir("signing", Context.MODE_PRIVATE).resolve(KEYSTORE_FILE_NAME)

    private companion object {
        const val KEYSTORE_FILE_NAME = "morphe.keystore"
    }
}
