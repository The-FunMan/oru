/*
 * Code hard forked from:
 * https://github.com/revanced/revanced-library/tree/06733072045c8016a75f232dec76505c0ba2e1cd
 */

package app.morphe.patcher.apk

import app.morphe.patcher.PatcherResult
import app.morphe.patcher.WritePhase
import app.morphe.patcher.WriteProgressListener
import app.morphe.patcher.WriteStageProgress
import app.morphe.patcher.apk.ApkSigner.newApkSigner
import app.morphe.patcher.apk.ApkSigner.newKeyStore
import app.morphe.patcher.apk.ApkSigner.newPrivateKeyCertificatePair
import app.morphe.patcher.util.FileUtils.safelyDelete
import com.android.apksig.ApkVerifier
import com.android.apksig.apk.ApkFormatException
import com.android.tools.build.apkzlib.zip.AlignmentRules
import com.android.tools.build.apkzlib.zip.CompressionMethod
import com.android.tools.build.apkzlib.zip.DataDescriptorType
import com.android.tools.build.apkzlib.zip.StoredEntry
import com.android.tools.build.apkzlib.zip.ZFile
import com.android.tools.build.apkzlib.zip.ZFileOptions
import com.android.tools.build.apkzlib.zip.compress.DeflateExecutionCompressor
import com.reandroid.apk.ApkModule
import com.reandroid.archive.writer.ZipAligner
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.NoSuchAlgorithmException
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.ForkJoinPool
import java.util.logging.Logger
import java.util.zip.ZipFile
import kotlin.time.Duration.Companion.days

/** Writes [input] with the [entries] and [remove]d names to the unsigned [output]. Returns false if it fails. */
internal typealias NativeWriter = (
    input: File,
    output: File,
    entries: List<NativePackager.Entry>,
    remove: Set<String>,
) -> Boolean

/**
 * Utility functions to work with APK files.
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
object ApkUtils {
    private val logger = Logger.getLogger(ApkUtils::class.java.name)

    private const val LIBRARY_EXTENSION = ".so"

    // Alignment for native libraries. A device with 16 KiB pages maps a stored library only
    // from a 16 KiB boundary, and the larger boundary is also a 4 KiB one.
    private const val LIBRARY_ALIGNMENT = 1024 * 16

    // Alignment for all other files.
    private const val DEFAULT_ALIGNMENT = 4

    // Within a few percent of the default level's size in markedly less time.
    private const val COMPRESSION_LEVEL = 3

    private val dexEntryName = Regex("""classes(?:\d+)?\.dex""")

    // v1 signature files, which the signer would otherwise leave stale beside its own signature.
    private val v1SignatureEntryName = Regex("""META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC)|SIG-[^/]*)""", RegexOption.IGNORE_CASE)

    private val zFileOptions =
        ZFileOptions().setAlignmentRule(
            AlignmentRules.compose(
                AlignmentRules.constantForSuffix(LIBRARY_EXTENSION, LIBRARY_ALIGNMENT),
                AlignmentRules.constant(DEFAULT_ALIGNMENT),
            ),
        ).setCompressor(DeflateExecutionCompressor(ForkJoinPool.commonPool(), COMPRESSION_LEVEL))

    /**
     * Writes this module to [file] aligned as [applyTo] aligns, so that it has nothing left to move.
     */
    internal fun ApkModule.writeAlignedApk(file: File) = createApkFileWriter(file).apply {
        zipAligner = ZipAligner().apply {
            setDefaultAlignment(DEFAULT_ALIGNMENT)
            setFileAlignment({ it.endsWith(LIBRARY_EXTENSION) }, LIBRARY_ALIGNMENT)
        }
    }.write()

    /**
     * Like [writeAlignedApk], but deletes [file] again when the write fails, so no partial APK is left behind.
     */
    internal fun ApkModule.writeAlignedApkOrDelete(file: File) {
        try {
            writeAlignedApk(file)
        } catch (t: Throwable) {
            file.safelyDelete()
            throw t
        }
    }

    /**
     * Applies the [PatcherResult] to the given [apkFile].
     *
     * The order of operation is as follows:
     * 1. Use resources.apk compiled by AAPT as the output base, when present.
     * 2. Delete resources staged for deletion.
     * 3. Write raw resources. This comes after the deletion so that an entry a patch deleted and
     *    then recreated ends up with the recreated content rather than removed.
     * 4. Write patched dex files.
     * 5. Realign the APK.
     *
     * Without a compiled resource APK, [apkFile] must be a copy of the input APK so that
     * unchanged entries remain available. Otherwise, the compiled resource APK replaces it.
     * Either way it holds the input's DEX files, which a complete patched DEX set replaces and
     * a partial one overwrites by name. v1 signature files are dropped, as signing replaces them.
     *
     * @param apkFile A copy of the patched APK, to apply the patched files to.
     */
    fun PatcherResult.applyTo(apkFile: File) {
        applyTo(apkFile, if (NativePackager.enabled) NativePackager::write else null)
    }

    /**
     * Applies the [PatcherResult] to the given [apkFile], reporting pack progress to [progressListener].
     * [progressListener] receives the fraction of the write stage, with the pack phase covering its share.
     */
    fun PatcherResult.applyToWithProgress(apkFile: File, progressListener: WriteProgressListener?) {
        val packWriterListener = progressListener?.let { listener ->
            val stageProgress = WriteStageProgress(
                listener = listener,
                initialSecondsDone = WritePhase.DEX_COMPILE.measuredSeconds + WritePhase.RESOURCE_COMPILE.measuredSeconds,
            )
            WriteProgressListener { packFraction ->
                stageProgress.packProgress(packFraction)
            }
        }
        val nativeWriter: NativeWriter? = if (NativePackager.enabled) {
            { input, output, entries, remove ->
                NativePackager.write(input, output, entries, remove, packWriterListener)
            }
        } else null
        applyTo(apkFile, nativeWriter, packWriterListener)
    }

    /**
     * [applyTo] with the native writer given, or with apkzlib alone when it is null. If [nativeWriter] fails,
     * [apkFile] is still unchanged, and the zip path writes it from the same input.
     */
    internal fun PatcherResult.applyTo(apkFile: File, nativeWriter: NativeWriter?) {
        applyTo(apkFile, nativeWriter, null)
    }

    internal fun PatcherResult.applyTo(
        apkFile: File,
        nativeWriter: NativeWriter?,
        progressListener: WriteProgressListener?,
    ) {
        // Dex streams without a file are spooled once, so the zip path can read them after a native failure.
        val spooled = mutableMapOf<PatcherResult.PatchedDexFile, File>()
        try {
            if (nativeWriter != null) {
                val applied = runCatching {
                    applyWithNativePackager(apkFile, nativeWriter, spooled)
                }.onFailure { error ->
                    logger.warning("Native packager failed to package APK, falling back to zip path: $error")
                }.getOrDefault(false)
                if (applied) return
            }
            applyWithZip(apkFile, spooled)
            progressListener?.onProgress(1f)
        } finally {
            dexFiles.forEach { dexFile -> runCatching { dexFile.stream.close() } }
            spooled.values.forEach { it.delete() }
        }
    }

    private fun PatcherResult.applyWithZip(apkFile: File, spooled: Map<PatcherResult.PatchedDexFile, File>) {
        resources.resourcesApk?.let { resourcesApk ->
            logger.info("Using compiled resource APK as output base")

            if (resourcesApk.canonicalFile != apkFile.canonicalFile) {
                resourcesApk.copyTo(apkFile, overwrite = true)
            }
        }

        ZFile.openReadWrite(apkFile, zFileOptions).use { targetApkZFile ->
            rewriteDataDescriptorEntries(targetApkZFile)
            // Entries are compressed in the background and cannot be deleted or moved until done.
            targetApkZFile.finishAllBackgroundTasks()

            val replacesAllDexFiles = dexFilesComplete && dexFiles.isNotEmpty()
            targetApkZFile.entries().filter { entry ->
                val name = entry.centralDirectoryHeader.name
                name.matches(v1SignatureEntryName) || replacesAllDexFiles && name.matches(dexEntryName)
            }.forEach(StoredEntry::delete)

            resources.let { resources ->
                // Delete resources that were staged for deletion, before adding the raw resources:
                // everything in otherResources is the newest version of its entry by construction.
                if (resources.deleteResources.isNotEmpty()) {
                    targetApkZFile.entries().filter { entry ->
                        entry.centralDirectoryHeader.name in resources.deleteResources
                    }.forEach(StoredEntry::delete)
                }

                // Add resources not compiled by AAPT.
                resources.otherResources?.let { otherResources ->
                    targetApkZFile.addAllRecursively(otherResources) { file ->
                        file.relativeTo(otherResources).invariantSeparatorsPath !in resources.doNotCompress
                    }
                }
            }

            // Run this after resource updates to ensure our dex files don't get overwritten.
            dexFiles.forEach { dexFile ->
                val spool = spooled[dexFile]
                if (spool != null) {
                    spool.inputStream().use { targetApkZFile.add(dexFile.name, it) }
                } else {
                    targetApkZFile.add(dexFile.name, dexFile.stream)
                }
            }

            targetApkZFile.finishAllBackgroundTasks()

            logger.info("Aligning APK")

            targetApkZFile.realign()

            logger.fine("Writing changes")
        }
    }

    /**
     * [applyTo] through the native packager. It writes the same entries as the zip path in one pass from the
     * resource APK, without copying it and without rewriting it through apkzlib, so it needs no realignment.
     * The result is unsigned, as the zip path's is. Its entries are written from fresh headers, so input entries
     * with a data descriptor need no rewrite.
     *
     * Returns false, with [apkFile] unchanged and no output left behind, when [nativeWriter] fails.
     */
    private fun PatcherResult.applyWithNativePackager(
        apkFile: File,
        nativeWriter: NativeWriter,
        spooled: MutableMap<PatcherResult.PatchedDexFile, File>,
    ): Boolean {
        logger.info("Writing APK with the native packager")
        val base = resources.resourcesApk ?: apkFile
        val directory = apkFile.absoluteFile.parentFile
        val staged = File.createTempFile("packaging", ".apk", directory)
        val scratch = mutableListOf(staged)
        try {
            val entries = linkedMapOf<String, NativePackager.Entry>()
            resources.otherResources?.let { otherResources ->
                // Directories are entries too, empty and stored, as the zip path writes them.
                val empty = File.createTempFile("directory", ".bin", directory).also { scratch += it }
                otherResources.walkTopDown().drop(1).forEach { file ->
                    val name = file.relativeTo(otherResources).invariantSeparatorsPath
                    if (file.isDirectory) {
                        entries["$name/"] = NativePackager.Entry("$name/", empty, stored = true)
                    } else {
                        entries[name] = NativePackager.Entry(name, file, stored = name in resources.doNotCompress)
                    }
                }
            }

            // Dex files come after resources so that they are not overwritten, as on the zip path.
            // The native writer maps its inputs, so a dex file without one on disk is spooled to a file.
            for (dexFile in dexFiles) {
                val file = dexFile.file ?: spoolDex(dexFile, directory, spooled)
                entries[dexFile.name] = NativePackager.Entry(dexFile.name, file, stored = false)
            }

            val replacesAllDexFiles = dexFilesComplete && dexFiles.isNotEmpty()
            val inputNames = ZipFile(base).use { zip -> zip.entries().asSequence().map { it.name }.toList() }
            val remove = inputNames.filterTo(mutableSetOf()) { name ->
                name.matches(v1SignatureEntryName) || replacesAllDexFiles && name.matches(dexEntryName)
            } + resources.deleteResources

            if (!nativeWriter(base, staged, entries.values.toList(), remove)) return false
            Files.move(staged.toPath(), apkFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            return true
        } finally {
            scratch.forEach { it.delete() }
        }
    }

    private fun spoolDex(
        dexFile: PatcherResult.PatchedDexFile,
        directory: File,
        spooled: MutableMap<PatcherResult.PatchedDexFile, File>,
    ): File {
        val file = File.createTempFile("dex", ".bin", directory)
        spooled[dexFile] = file
        dexFile.stream.use { input -> file.outputStream().use { input.copyTo(it) } }
        return file
    }

    /**
     * Rewrites every entry that was written with a data descriptor (general purpose bit 3).
     *
     * Once the archive changes, apkzlib writes each central directory header again, without
     * bit 3, but it leaves the local header of an entry it does not move as it was, bit 3 and
     * zeroed sizes included. The headers then disagree and apksig refuses to sign the APK. Old
     * build tools wrote deflated entries this way, and entries reused from such an input keep it.
     * Adding the entry again makes apkzlib write both headers itself.
     */
    private fun rewriteDataDescriptorEntries(zFile: ZFile) {
        zFile.entries()
            .filter { it.dataDescriptorType != DataDescriptorType.NO_DATA_DESCRIPTOR }
            .forEach { entry ->
                val name = entry.centralDirectoryHeader.name
                val deflated = entry.centralDirectoryHeader.compressionInfoWithWait.method ==
                        CompressionMethod.DEFLATE
                val contents = entry.read()
                zFile.add(name, contents.inputStream(), deflated)
            }
    }

    /**
     * Creates a new private key and certificate pair and saves it to the keystore in [keyStoreDetails].
     *
     * @param privateKeyCertificatePairDetails The details for the private key and certificate pair.
     * @param keyStoreDetails The details for the keystore.
     *
     * @return The newly created private key and certificate pair.
     */
    internal fun newPrivateKeyCertificatePair(
        privateKeyCertificatePairDetails: PrivateKeyCertificatePairDetails,
        keyStoreDetails: KeyStoreDetails,
    ) = newPrivateKeyCertificatePair(
        privateKeyCertificatePairDetails.commonName,
        privateKeyCertificatePairDetails.validUntil,
    ).also { privateKeyCertificatePair ->
        val keyStoreFile = keyStoreDetails.keyStore
        // Written beside the target and moved over it, so an interrupted write cannot leave a
        // truncated keystore that the next run takes for an existing one
        val stagingFile = File(keyStoreFile.absoluteFile.parentFile, "${keyStoreFile.name}.tmp")
        try {
            stagingFile.outputStream().use { stream ->
                newKeyStore(
                    setOf(
                        ApkSigner.KeyStoreEntry(
                            keyStoreDetails.alias,
                            keyStoreDetails.password,
                            privateKeyCertificatePair,
                        ),
                    ),
                ).store(stream, keyStoreDetails.keyStorePassword?.toCharArray())
            }
            try {
                Files.move(
                    stagingFile.toPath(),
                    keyStoreFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(stagingFile.toPath(), keyStoreFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            stagingFile.delete()
        }
    }

    /**
     * Reads the private key and certificate pair from an existing keystore.
     *
     * @param keyStoreDetails The details for the keystore.
     *
     * @return The private key and certificate pair.
     */
    internal fun readPrivateKeyCertificatePairFromKeyStore(
        keyStoreDetails: KeyStoreDetails,
    ) = ApkSigner.readPrivateKeyCertificatePair(
        keyStoreDetails.keyStore.inputStream().use { stream ->
            ApkSigner.readKeyStore(stream, keyStoreDetails.keyStorePassword)
        },
        keyStoreDetails.alias,
        keyStoreDetails.password,
    )

    /**
     * Signs [inputApkFile] with the given options and saves the signed apk to [outputApkFile].
     * If [KeyStoreDetails.keyStore] does not exist,
     * a new private key and certificate pair will be created and saved to the keystore.
     *
     * @param inputApkFile The apk file to sign.
     * @param outputApkFile The file to save the signed apk to. Passing [inputApkFile] signs it in place.
     * @param signer The name of the signer.
     * @param keyStoreDetails The details for the keystore.
     * @param minSdkVersion The lowest API level the APK will be installed on,
     *   if higher than the minimum it declares. From API level 24 no v1 signature is needed.
     */
    @JvmOverloads
    fun signApk(
        inputApkFile: File,
        outputApkFile: File,
        signer: String,
        keyStoreDetails: KeyStoreDetails,
        minSdkVersion: Int = 0,
    ) = signApk(inputApkFile, outputApkFile, signer, keyStoreDetails, minSdkVersion, null)

    fun signApk(
        inputApkFile: File,
        outputApkFile: File,
        signer: String,
        keyStoreDetails: KeyStoreDetails,
        minSdkVersion: Int,
        onProgress: ((Float) -> Unit)?,
    ) = newApkSigner(
        signer,
        if (keyStoreDetails.keyStore.exists()) {
            readPrivateKeyCertificatePairFromKeyStore(keyStoreDetails)
        } else {
            newPrivateKeyCertificatePair(PrivateKeyCertificatePairDetails(), keyStoreDetails)
        },
    ).signApk(inputApkFile, outputApkFile, minSdkVersion, onProgress)

    /**
     * Verifies the signature of [apkFile] as a device running [platformVersion] checks it on
     * install, and returns the certificates the APK is signed with.
     *
     * A signer that rotated its key reports its whole lineage, oldest first, as Android reports
     * it for an installed package, so a certificate the app was once signed with still matches.
     *
     * @param apkFile The APK file to verify.
     * @param platformVersion The Android API level to verify for, such as `Build.VERSION.SDK_INT`.
     *
     * @return The certificates of [apkFile], an empty list if it is unsigned, malformed or its
     *   signature does not verify, or null if it could not be read, which a later attempt may not repeat.
     */
    fun verifiedSigningCertificates(apkFile: File, platformVersion: Int): List<X509Certificate>? =
        try {
            val result = ApkVerifier.Builder(apkFile)
                .setMinCheckedPlatformVersion(platformVersion)
                .setMaxCheckedPlatformVersion(platformVersion)
                .build()
                .verify()

            if (result.isVerified) {
                result.signingCertificateLineage?.certificatesInLineage ?: result.signerCertificates
            } else {
                emptyList()
            }
        } catch (_: ApkFormatException) {
            emptyList()
        } catch (e: IOException) {
            logger.warning("Failed to read $apkFile for verification: $e")
            null
        } catch (e: NoSuchAlgorithmException) {
            logger.warning("Failed to verify $apkFile: $e")
            null
        }

    /**
     * Details for a keystore.
     *
     * @param keyStore The file to save the keystore to.
     * @param keyStorePassword The password for the keystore.
     * @param alias The alias of the key store entry to use for signing.
     * @param password The password for recovering the signing key.
     */
    class KeyStoreDetails(
        val keyStore: File,
        val keyStorePassword: String? = null,
        val alias: String,
        val password: String,
    )

    /**
     * Details for a private key and certificate pair.
     *
     * @param commonName The common name for the certificate saved in the keystore.
     * @param validUntil The date until which the certificate is valid.
     */
    class PrivateKeyCertificatePairDetails(
        val commonName: String = "Morphe",
        val validUntil: Date = Date(System.currentTimeMillis() + (365.days * 8).inWholeMilliseconds),
    )
}
