package app.morphe.patcher.apk

import app.morphe.patcher.WriteProgressListener
import com.android.apksig.util.DataSources
import java.io.File
import java.io.RandomAccessFile
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.logging.Logger
import com.android.apksig.apk.ApkUtils as ApkSigUtils

/** The JNI entry points of native-packager/jni. Use [NativePackager], which checks that the library is loaded. */
internal object NativePackagerLib {
    @JvmStatic
    external fun write(
        input: String,
        output: String,
        names: Array<String>,
        paths: Array<String>,
        stored: BooleanArray,
        remove: Array<String>,
    ): Long

    @JvmStatic
    external fun writeWithProgress(
        input: String,
        output: String,
        names: Array<String>,
        paths: Array<String>,
        stored: BooleanArray,
        remove: Array<String>,
        callback: WriteProgressListener?,
    ): Long

    @JvmStatic
    external fun signInPlace(path: String, privateKey: ByteArray, certificate: ByteArray): Long

    @JvmStatic
    external fun signInPlaceWithProgress(
        path: String,
        privateKey: ByteArray,
        certificate: ByteArray,
        callback: WriteProgressListener?,
    ): Long
}

/**
 * The native APK writer and v2 signer. The library is `liboru_packager.so` beside the dex index library
 * (`oru.native.lib`), or the file named by `oru.packager.lib`. Without it, or with `oru.packager=jvm`,
 * [enabled] is false and the patcher writes and signs with apkzlib and apksig as before.
 */
internal object NativePackager {
    private val logger = Logger.getLogger(NativePackager::class.java.name)

    private const val LIBRARY_PROPERTY = "oru.packager.lib"
    private const val DEX_INDEX_PROPERTY = "oru.native.lib"
    private const val LIBRARY_FILE = "liboru_packager.so"
    private const val SWITCH_PROPERTY = "oru.packager"

    /** One entry the writer adds or replaces, with the compression it is written with. */
    class Entry(val name: String, val file: File, val stored: Boolean)

    @Volatile
    private var library = false

    /** Whether the native writer and signer are loaded and not switched off with `-Doru.packager=jvm`. */
    val enabled: Boolean
        get() = (library || load()) && System.getProperty(SWITCH_PROPERTY) != "jvm"

    /** Loads the library once it is found, and looks again on each call until then. */
    @Synchronized
    private fun load(): Boolean {
        if (library) return true
        val path = System.getProperty(LIBRARY_PROPERTY)
            ?: System.getProperty(DEX_INDEX_PROPERTY)?.let { File(it).resolveSibling(LIBRARY_FILE).path }
            ?: return false
        if (!File(path).isFile) return false
        library = try {
            System.load(File(path).absolutePath)
            true
        } catch (error: UnsatisfiedLinkError) {
            logger.warning("Native packager is not usable, the JVM path is used: $error")
            false
        }
        return library
    }

    /**
     * Writes [input] to the unsigned [output] with [entries] replacing or adding entries and the [remove]d
     * names dropped. Entries keep the order of [input] and new entries follow it. Returns false, after a
     * warning, if the native writer fails; [output] is then incomplete and must be discarded.
     */
    fun write(
        input: File,
        output: File,
        entries: List<Entry>,
        remove: Set<String>,
    ): Boolean = write(input, output, entries, remove, null)

    /**
     * Like [write], but reports entry packing progress to [progress].
     */
    fun write(
        input: File,
        output: File,
        entries: List<Entry>,
        remove: Set<String>,
        progress: WriteProgressListener?,
    ): Boolean = nativeCall("write") {
        val names = Array(entries.size) { entries[it].name }
        val paths = Array(entries.size) { entries[it].file.absolutePath }
        val stored = BooleanArray(entries.size) { entries[it].stored }
        val removeArray = remove.toTypedArray()
        try {
            NativePackagerLib.writeWithProgress(
                input.absolutePath,
                output.absolutePath,
                names,
                paths,
                stored,
                removeArray,
                progress,
            )
        } catch (_: UnsatisfiedLinkError) {
            NativePackagerLib.write(
                input.absolutePath,
                output.absolutePath,
                names,
                paths,
                stored,
                removeArray,
            )
        }
    }

    /**
     * Signs the unsigned APK [apk] in place with APK Signature Scheme v2 for [privateKey] and [certificate].
     * The encoded key is wiped after the call. Returns false when the key has no PKCS#8 encoding or the native
     * signer fails, in which case the caller signs with apksig. A failed signer may have overwritten the
     * central directory, so [apk] is restored to its unsigned bytes before returning false. The restore is
     * best effort: when it fails, a warning is logged and [apk] keeps the native signer's partial writes, so the
     * caller must not assume [apk] is intact after a false result.
     */
    fun signInPlace(
        apk: File,
        privateKey: PrivateKey,
        certificate: X509Certificate,
        sign: (String, ByteArray, ByteArray) -> Long,
    ): Boolean = signInPlace(apk, privateKey, certificate, null, sign)

    /**
     * Like [signInPlace], but reports signing hash progress to [progress].
     */
    fun signInPlace(
        apk: File,
        privateKey: PrivateKey,
        certificate: X509Certificate,
        progress: WriteProgressListener? = null,
        sign: (String, ByteArray, ByteArray) -> Long = { path, key, cert ->
            try {
                NativePackagerLib.signInPlaceWithProgress(path, key, cert, progress)
            } catch (_: UnsatisfiedLinkError) {
                NativePackagerLib.signInPlace(path, key, cert)
            }
        },
    ): Boolean {
        val key = privateKey.encoded ?: return false
        try {
            var tail: CentralDirectory? = null
            val signed = nativeCall("sign") {
                tail = readCentralDirectory(apk)
                sign(apk.absolutePath, key, certificate.encoded)
            }
            if (!signed) {
                tail?.let { bytes ->
                    restoreCentralDirectory(apk, bytes).onFailure { error ->
                        logger.warning("Could not restore ${apk.name} after the native signer failed, it may be corrupt: $error")
                    }
                }
            }
            return signed
        } finally {
            key.fill(0)
        }
    }

    /** The bytes the native signer overwrites: the central directory and the end of central directory record. */
    private class CentralDirectory(val offset: Long, val bytes: ByteArray, val fileLength: Long)

    private fun readCentralDirectory(apk: File): CentralDirectory = RandomAccessFile(apk, "r").use { file ->
        val offset = ApkSigUtils.findZipSections(DataSources.asDataSource(file)).zipCentralDirectoryOffset
        val bytes = ByteArray((file.length() - offset).toInt())
        file.seek(offset)
        file.readFully(bytes)
        CentralDirectory(offset, bytes, file.length())
    }

    private fun restoreCentralDirectory(apk: File, tail: CentralDirectory) = runCatching {
        RandomAccessFile(apk, "rw").use { file ->
            file.seek(tail.offset)
            file.write(tail.bytes)
            file.setLength(tail.fileLength)
        }
    }

    /**
     * Runs [call] and reports whether it returned, logging the cause of a failure. A library that cannot be linked
     * is a [LinkageError]; an [Error] such as [OutOfMemoryError] is not a failure of the native path and propagates.
     */
    private inline fun nativeCall(action: String, call: () -> Unit): Boolean {
        val failure: Throwable = try {
            call()
            return true
        } catch (error: Exception) {
            error
        } catch (error: LinkageError) {
            error
        }
        logger.warning("Native packager failed to $action, the JVM path is used: $failure")
        return false
    }
}
