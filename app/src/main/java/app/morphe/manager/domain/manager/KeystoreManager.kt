/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/domain/manager/KeystoreManager.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.domain.manager

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import app.morphe.manager.domain.apk.apkFileStampOrNull
import app.morphe.manager.util.sha256Fingerprint
import app.morphe.patcher.apk.ApkSigner
import app.morphe.patcher.apk.ApkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.*
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import java.security.cert.Certificate
import java.security.cert.X509Certificate

/**
 * Replaces [target] with [data] through a temporary file beside it, so a write cut short never
 * leaves a truncated keystore where the signing key lives.
 */
internal fun replaceFileAtomically(target: Path, data: ByteArray) {
    val temp = Files.createTempFile(target.parent, target.fileName.toString(), ".tmp")
    try {
        Files.write(temp, data)
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temp)
    }
}

/**
 * What tells one signing key from another at a glance.
 *
 * @param sha256 Fingerprint of the key's certificate in lowercase hex, the same form the package
 *        manager reports for an installed package.
 * @param createdAt When the certificate became valid, which for a key Morphe generated is when
 *        it was made. Null when the certificate does not say.
 */
data class SigningKeyInfo(
    val alias: String,
    val sha256: String,
    val createdAt: Long?
)

class KeystoreManager(app: Application, private val prefs: PreferencesManager) {
    companion object Constants {
        /** Default alias and password for the keystore. */
        const val DEFAULT = "Morphe"

        private const val TAG = "Morphe Keystore"
    }

    private val keystorePath =
        app.getDir("signing", Context.MODE_PRIVATE).resolve("morphe.keystore")

    // Reading the keystore goes through BouncyCastle, so the fingerprints are kept until the
    // file itself changes rather than re-read for every app a home refresh inspects
    @Volatile
    private var cachedCertificateHashes: Pair<String, Set<String>>? = null

    // The signer is kept for the same reason, until the keystore or its settings change
    private var cachedSigner: Pair<List<Any?>, ApkSigner.Signer>? = null
    private val signerLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private suspend fun updatePrefs(alias: String, pass: String, keystorePw: String) = prefs.edit {
        prefs.keystoreAlias.value = alias
        prefs.keystorePass.value = pass
        prefs.keystorePassword.value = keystorePw
    }

    private suspend fun signingDetails(path: File = keystorePath) = ApkUtils.KeyStoreDetails(
        keyStore = path,
        keyStorePassword = prefs.keystorePassword.get().ifEmpty { null },
        alias = prefs.keystoreAlias.get(),
        password = prefs.keystorePass.get()
    )

    /**
     * Signs [apk] in place for this device, which never needs a v1 signature. Only ever handed
     * what the patcher just wrote, whose headers it already rewrites where the signer would reject them.
     */
    suspend fun sign(apk: File, onProgress: ((Float) -> Unit)? = null) = withContext(Dispatchers.Default) {
        signer()?.signApk(apk, apk, Build.VERSION.SDK_INT, onProgress)
            // Creates the keystore on the first patch
            ?: run {
                // ORU: a keystore made here takes the oru alias and password, and is stored with them
                // so later signing reads the same values. Only reached when no keystore file exists.
                OruEndpoints.current.newKeystoreAlias?.let { updatePrefs(it, it, prefs.keystorePassword.get()) }
                ApkUtils.signApk(apk, apk, prefs.keystoreAlias.get(), signingDetails(), Build.VERSION.SDK_INT, onProgress)
            }
    }

    /** Starts loading the signing key, which takes BouncyCastle a while, so signing need not wait for it. */
    fun preloadSigner() {
        scope.launch { runCatching { signer() } }
    }

    private suspend fun signer(): ApkSigner.Signer? = signerLock.withLock {
        withContext(Dispatchers.IO) {
            val stamp = keystorePath.apkFileStampOrNull() ?: return@withContext null
            val details = signingDetails()
            val key = listOf(stamp.cacheKey, details.alias, details.password, details.keyStorePassword)
            cachedSigner?.takeIf { it.first == key }?.second
                ?: ApkSigner.newApkSigner(
                    details.alias,
                    ApkSigner.readPrivateKeyCertificatePair(readKeyStore(), details.alias, details.password)
                ).also { cachedSigner = key to it }
        }
    }

    suspend fun import(alias: String, pass: String, keystorePw: String = "", keystore: InputStream): Boolean {
        val keystoreData = withContext(Dispatchers.IO) { keystore.readBytes() }

        try {
            val ks = ApkSigner.readKeyStore(ByteArrayInputStream(keystoreData), null)

            ApkSigner.readPrivateKeyCertificatePair(ks, alias, pass)
        } catch (_: UnrecoverableKeyException) {
            return false
        } catch (_: IllegalArgumentException) {
            return false
        }

        val target = keystorePath.toPath()
        val previous = withContext(Dispatchers.IO) {
            target.takeIf(Files::exists)?.let(Files::readAllBytes)
        }
        withContext(Dispatchers.IO) {
            replaceFileAtomically(target, keystoreData)
        }

        updatePrefs(alias, pass, keystorePw)
        // The preference store drops a failed write without reporting it, so the stored
        // credentials are read back to find out whether they describe the new keystore
        if (storedKeystoreCredentials() == Triple(alias, pass, keystorePw)) return true

        withContext(Dispatchers.IO) {
            if (previous != null) replaceFileAtomically(target, previous) else Files.deleteIfExists(target)
        }
        return false
    }

    private suspend fun storedKeystoreCredentials() =
        Triple(prefs.keystoreAlias.get(), prefs.keystorePass.get(), prefs.keystorePassword.get())

    /**
     * SHA-256 fingerprints of every certificate the signing keystore holds, in the same form the
     * package manager reports for an installed package.
     *
     * Everything Morphe signs carries one of them, which is the only thing still identifying a
     * patched build once both the patched APK and the original it was built from are gone.
     */
    suspend fun signingCertificateHashes(): Set<String> = withContext(Dispatchers.IO) {
        val stamp = keystorePath.apkFileStampOrNull() ?: return@withContext emptySet()
        cachedCertificateHashes?.takeIf { it.first == stamp.cacheKey }?.let {
            return@withContext it.second
        }

        val hashes = try {
            val keyStore = readKeyStore()

            // Every alias is read, because an imported keystore can hold the key an earlier
            // patched build was signed with next to the one patching uses now
            keyStore.aliases().asSequence().mapNotNullTo(mutableSetOf()) { alias ->
                keyStore.getCertificate(alias)?.sha256()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read signing certificates", e)
            emptySet()
        }

        cachedCertificateHashes = stamp.cacheKey to hashes
        hashes
    }

    /** The key patching signs with. Null until the first patch creates one, and when it cannot be read. */
    suspend fun signingKeyInfo(): SigningKeyInfo? = withContext(Dispatchers.IO) {
        if (!keystorePath.exists()) return@withContext null
        try {
            val alias = prefs.keystoreAlias.get()
            val certificate = readKeyStore().getCertificate(alias) ?: return@withContext null
            SigningKeyInfo(
                alias = alias,
                sha256 = certificate.sha256(),
                createdAt = (certificate as? X509Certificate)?.notBefore?.time
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read the signing key", e)
            null
        }
    }

    private suspend fun readKeyStore(): KeyStore {
        val keyStorePassword = prefs.keystorePassword.get().ifEmpty { null }
        return keystorePath.inputStream().use { ApkSigner.readKeyStore(it, keyStorePassword) }
    }

    private fun Certificate.sha256(): String = encoded.sha256Fingerprint()

    suspend fun export(target: OutputStream) {
        withContext(Dispatchers.IO) {
            Files.copy(keystorePath.toPath(), target)
        }
    }
}
