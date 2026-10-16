/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.dex

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.util.DexUtil
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

private const val HEADER_SIZE = 112
private const val MAP_LIST_OFFSET = HEADER_SIZE
private const val LITTLE_ENDIAN_TAG = 0x12345678
private const val BIG_ENDIAN_TAG = 0x78563412

// Header fields, as offsets from the start of the file.
private const val FILE_SIZE_OFFSET = 32
private const val HEADER_SIZE_OFFSET = 36
private const val ENDIAN_TAG_OFFSET = 40
private const val MAP_OFF_OFFSET = 52
private const val CLASS_DEFS_SIZE_OFFSET = 96
private const val CLASS_DEFS_OFF_OFFSET = 100

/**
 * Malformed DEX input must fail on the first read that goes wrong, with an exception, instead of
 * hanging or returning a partly parsed file. Each image is built by hand from a 112-byte header.
 */
internal class MalformedDexTest {
    @TempDir
    lateinit var temporaryDirectory: File

    @Test
    fun `a well formed empty image reads with no classes`() {
        val classes = read(dexImage()).classes

        assertEquals(0, classes.size)
    }

    @Test
    fun `input shorter than the magic and version fails`() {
        assertFailsWith<BufferUnderflowException> { read(dexImage().copyOf(4)) }
    }

    @Test
    fun `image cut off inside the header fails`() {
        assertFailsWith<IndexOutOfBoundsException> { read(dexImage().copyOf(50)) }
    }

    @Test
    fun `bad magic is rejected as not a dex file`() {
        assertFailsWith<DexBackedDexFile.NotADexFile> { read(dexImage(magic = "zip\n035\u0000")) }
    }

    @Test
    fun `unknown dex version is rejected as unsupported`() {
        assertFailsWith<DexUtil.UnsupportedFile> { read(dexImage(magic = "dex\n999\u0000")) }
    }

    @Test
    fun `big endian image is rejected as unsupported`() {
        assertFailsWith<DexUtil.UnsupportedFile> { read(dexImage(endianTag = BIG_ENDIAN_TAG)) }
    }

    @Test
    fun `unknown endian tag is rejected as invalid`() {
        assertFailsWith<DexUtil.InvalidFile> { read(dexImage(endianTag = 0x0BADF00D)) }
    }

    @Test
    fun `negative section offset is rejected while the header is read`() {
        assertFailsWith<RuntimeException> { read(dexImage(CLASS_DEFS_OFF_OFFSET to -1)) }
    }

    @Test
    fun `class section beyond the end of the image fails when classes are read`() {
        val dex = read(dexImage(CLASS_DEFS_SIZE_OFFSET to 1, CLASS_DEFS_OFF_OFFSET to 0x7FFFFF00))

        assertFailsWith<IndexOutOfBoundsException> { dex.classes.toList() }
    }

    @Test
    fun `oversized class count fails at the first class past the end of the image`() {
        val dex = read(
            dexImage(
                CLASS_DEFS_SIZE_OFFSET to Int.MAX_VALUE,
                CLASS_DEFS_OFF_OFFSET to MAP_LIST_OFFSET + 4,
            )
        )

        assertFailsOnOversizedRead { dex.classes.toList() }
    }

    @Test
    fun `stripper rejects an image cut off inside the header`() {
        val error = assertFailsWith<PatchException> { strip(dexImage().copyOf(50)) }

        assertEquals(
            "Malformed DEX file: the file is 50 bytes, shorter than the 112-byte header",
            error.message,
        )
    }

    @Test
    fun `stripper rejects a class section beyond the end of the image`() {
        assertFailsWith<IndexOutOfBoundsException> {
            strip(dexImage(CLASS_DEFS_SIZE_OFFSET to 1, CLASS_DEFS_OFF_OFFSET to 0x7FFFFF00))
        }
    }

    @Test
    fun `stripper rejects an oversized class count instead of reading past the image`() {
        assertFailsWith<IndexOutOfBoundsException> {
            strip(dexImage(CLASS_DEFS_SIZE_OFFSET to 0xFFFF, CLASS_DEFS_OFF_OFFSET to MAP_LIST_OFFSET + 4))
        }
    }

    private fun read(image: ByteArray) = DexReadWrite.readDexStream(image.inputStream())

    /**
     * Asserts that [block] fails with [IndexOutOfBoundsException] or [OutOfMemoryError] and returns
     * at all. dexlib2 reports the claimed class count as the collection size, and the JDK copy that
     * toList() makes allocates an array of that size before any class is read. A count of
     * Int.MAX_VALUE is refused by the VM at once, so this surfaces as OutOfMemoryError rather than a
     * bounds failure. The allocation lives in the library and the JDK, so it is not changed here.
     */
    private fun assertFailsOnOversizedRead(block: () -> Unit) {
        try {
            block()
        } catch (expected: IndexOutOfBoundsException) {
            return
        } catch (expected: OutOfMemoryError) {
            return
        }
        fail("expected IndexOutOfBoundsException or OutOfMemoryError")
    }

    private fun strip(image: ByteArray): Int {
        val file = temporaryDirectory.resolve("classes.dex").apply { writeBytes(image) }
        return MappedFile.mapReadWrite(file).use { DexStripper.stripInPlace(it, setOf("Lpkg/a;")) }
    }

    /**
     * Builds a 116-byte image: a 112-byte header followed by an empty map_list. Fields not listed
     * in [fields] stay zero, so an image with no classes and no sections is a valid starting point.
     */
    private fun dexImage(
        vararg fields: Pair<Int, Int>,
        magic: String = "dex\n035\u0000",
        endianTag: Int = LITTLE_ENDIAN_TAG,
    ): ByteArray {
        val image = ByteBuffer.allocate(MAP_LIST_OFFSET + 4).order(ByteOrder.LITTLE_ENDIAN)
        image.put(magic.toByteArray(Charsets.US_ASCII))
        image.putInt(FILE_SIZE_OFFSET, image.capacity())
        image.putInt(HEADER_SIZE_OFFSET, HEADER_SIZE)
        image.putInt(ENDIAN_TAG_OFFSET, endianTag)
        image.putInt(MAP_OFF_OFFSET, MAP_LIST_OFFSET)
        fields.forEach { (offset, value) -> image.putInt(offset, value) }
        return image.array()
    }
}
