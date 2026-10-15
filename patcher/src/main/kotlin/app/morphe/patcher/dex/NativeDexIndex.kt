package app.morphe.patcher.dex

import java.io.Closeable
import java.io.File
import java.util.logging.Logger

/** The calls that open a native index, so that a test can stand in for the library. */
internal interface NativeDexIndexOps {
    fun open(paths: Array<String>): Long
    fun close(handle: Long)
    fun stats(handle: Long): LongArray
    fun descriptors(handle: Long): Array<String>
}

internal object NativeDexIndexLib {
    @JvmStatic external fun open(paths: Array<String>): Long
    @JvmStatic external fun close(handle: Long)
    @JvmStatic external fun stats(handle: Long): LongArray
    @JvmStatic external fun descriptors(handle: Long): Array<String>
    @JvmStatic external fun withString(handle: Long, string: String): IntArray
    @JvmStatic external fun withTypeHash(handle: Long, hash: Int): IntArray
    @JvmStatic external fun withMemberHash(handle: Long, hash: Int): IntArray
    @JvmStatic external fun withLiteralHash(handle: Long, hash: Int): IntArray
    @JvmStatic external fun withAnyString(handle: Long): IntArray
}

internal object NativeDexIndexCalls : NativeDexIndexOps {
    override fun open(paths: Array<String>) = NativeDexIndexLib.open(paths)
    override fun close(handle: Long) = NativeDexIndexLib.close(handle)
    override fun stats(handle: Long) = NativeDexIndexLib.stats(handle)
    override fun descriptors(handle: Long) = NativeDexIndexLib.descriptors(handle)
}

/**
 * Per-class instruction indexes of the input dex files, computed natively: the string literals, the hashes of the
 * types, member names and wide literals each class uses (the same hashes PatchClasses indexes). A class is identified by its ordinal, the position of its descriptor in
 * [descriptors].
 *
 * Returns null from [openOrNull] when the native library is not available, and callers then index the classes
 * themselves, as before.
 */
internal class NativeDexIndex private constructor(
    private var handle: Long,
    val descriptors: Array<String>,
) : Closeable {
    fun withString(string: String): IntArray = NativeDexIndexLib.withString(handle, string)
    fun withTypeHash(hash: Int): IntArray = NativeDexIndexLib.withTypeHash(handle, hash)
    fun withMemberHash(hash: Int): IntArray = NativeDexIndexLib.withMemberHash(handle, hash)
    fun withLiteralHash(hash: Int): IntArray = NativeDexIndexLib.withLiteralHash(handle, hash)
    fun withAnyString(): IntArray = NativeDexIndexLib.withAnyString(handle)

    override fun close() {
        if (handle != 0L) NativeDexIndexLib.close(handle)
        handle = 0L
    }

    companion object {
        /** Path of the native library, for hosts that do not ship it as `liboru_patcher`. */
        const val LIBRARY_PROPERTY = "oru.native.lib"

        /** Set to false to index in Kotlin, as morphe-patcher does. */
        const val ENABLED_PROPERTY = "oru.native.index"

        private val logger = Logger.getLogger(NativeDexIndex::class.java.name)

        private val libraryLoaded: Boolean by lazy {
            try {
                val path = System.getProperty(LIBRARY_PROPERTY)
                if (path != null) System.load(File(path).absolutePath) else System.loadLibrary("oru_patcher")
                true
            } catch (e: UnsatisfiedLinkError) {
                logger.info("Native dex index is not available: ${e.message}")
                false
            } catch (_: SecurityException) {
                false
            }
        }

        fun openOrNull(dexFiles: List<File>): NativeDexIndex? {
            if (System.getProperty(ENABLED_PROPERTY, "true").toBoolean().not() || !libraryLoaded) return null
            // A panic inside the native library surfaces as a RuntimeException, so the Kotlin index takes over
            return try {
                openWith(NativeDexIndexCalls, dexFiles)
            } catch (e: RuntimeException) {
                logger.warning("Native dex index failed, using the Kotlin index: ${e.message}")
                null
            }
        }

        /** Opens the index through [lib]; a handle is closed again on every path that does not return it. */
        internal fun openWith(lib: NativeDexIndexOps, dexFiles: List<File>): NativeDexIndex? {
            return try {
                val handle = lib.open(dexFiles.map { it.absolutePath }.toTypedArray())
                if (handle == 0L) return null
                try {
                    val stats = lib.stats(handle)
                    if (stats[2] != 0L) {
                        // The native decoder lost its place in some methods, so its answers cannot be trusted
                        logger.warning("Native dex index disagrees with the code items of ${stats[2]} methods, not using it")
                        lib.close(handle)
                        return null
                    }
                    NativeDexIndex(handle, lib.descriptors(handle)).also {
                        logger.info("Native dex index: ${stats[0]} classes, ${stats[1]} methods")
                    }
                } catch (e: Throwable) {
                    lib.close(handle)
                    throw e
                }
            } catch (_: UnsatisfiedLinkError) {
                null
            }
        }
    }
}
