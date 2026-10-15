/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.dex

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.writer.io.FileDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class DexStripperTest {
    @TempDir
    lateinit var temporaryDirectory: File

    @Test
    fun `strips ascii and non-ascii descriptors and keeps the rest`() {
        val types = listOf("Lpkg/a;", "Lpkg/b;", "Lpkg/a/b;", "Lpkg/café;", "Lc;")
        val file = temporaryDirectory.resolve("classes.dex")
        DexPool(Opcodes.getDefault()).apply {
            types.forEach { type ->
                internClass(ImmutableClassDef(type, AccessFlags.PUBLIC.value, "Ljava/lang/Object;", null, null, null, null, null))
            }
            writeTo(FileDataStore(file))
        }

        MappedFile.mapReadWrite(file).use { mapped ->
            val stripped = DexStripper.stripInPlace(mapped, setOf("Lpkg/a;", "Lpkg/café;"))
            assertEquals(2, stripped)
        }

        val remaining = DexBackedDexFile.fromInputStream(null, file.inputStream().buffered()).classes.mapTo(HashSet()) { it.type }
        assertEquals(setOf("Lpkg/b;", "Lpkg/a/b;", "Lc;"), remaining)
    }

    @Test
    fun `dex file shorter than its header is reported as malformed`() {
        val file = temporaryDirectory.resolve("truncated.dex")
        DexPool(Opcodes.getDefault()).apply {
            internClass(ImmutableClassDef("Lpkg/a;", AccessFlags.PUBLIC.value, "Ljava/lang/Object;", null, null, null, null, null))
            writeTo(FileDataStore(file))
        }
        file.writeBytes(file.readBytes().copyOf(60))

        MappedFile.mapReadWrite(file).use { mapped ->
            val error = assertFailsWith<PatchException> {
                DexStripper.stripInPlace(mapped, setOf("Lpkg/a;"))
            }
            assertContains(error.message.orEmpty(), "Malformed DEX file")
        }
    }
}
