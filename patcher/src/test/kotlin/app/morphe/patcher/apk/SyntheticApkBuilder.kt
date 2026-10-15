/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.apk

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import com.reandroid.arsc.chunk.TableBlock
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds small APK-shaped zips for tests, so that tests need no APK files on disk.
 *
 * [write] stores exactly the entries it is given, in order. [dexBytes] and [resourceTable] make
 * content that the patcher parses for real. No AndroidManifest.xml is built here: a binary manifest
 * needs ARSCLib's encoder, so a test that needs one passes its bytes as an entry.
 */
internal object SyntheticApkBuilder {
    /** A DEX file with one empty public class per descriptor in [classTypes]. */
    fun dexBytes(vararg classTypes: String): ByteArray {
        val pool = DexPool(Opcodes.getDefault())
        classTypes.forEach { type ->
            pool.internClass(
                ImmutableClassDef(
                    type, AccessFlags.PUBLIC.value, "Ljava/lang/Object;", null, null, null, null, null
                )
            )
        }
        return MemoryDataStore().also { pool.writeTo(it) }.data
    }

    /** A resources.arsc with one package holding one string, enough for ARSCLib to read a table. */
    fun resourceTable(): ByteArray {
        val table = TableBlock()
        table.newPackage(0x7f, "com.test.app").getOrCreate("", "string", "app_name").setValueAsString("Test")
        table.refresh()
        return table.bytes
    }

    /** Writes a zip archive at [file] holding [entries], keyed by entry name, in map order. */
    fun write(file: File, entries: Map<String, ByteArray>): File {
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }
}
