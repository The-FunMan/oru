/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.resource.processor

import app.morphe.patcher.resource.PublicXmlManager
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.random.Random

/**
 * Compares the streaming scans with the whole-file decisions they replaced. The legacy functions below are
 * the code as it stood before the streaming change, kept verbatim so the comparison does not depend on the
 * old code still existing.
 */
internal class IdAndSanitizeScanDifferentialTest {
    @TempDir
    lateinit var tempDir: File

    private val bufferSizes = listOf(1, 2, 3, 4, 5, 7, 8, 13, 64, 4096, 64 * 1024)

    @Test
    fun `id byte scan matches the legacy scan for every buffer size`() {
        for ((name, bytes) in corpus()) {
            val expected = legacyIdByteHit(bytes)
            for (size in bufferSizes) {
                assertEquals(
                    expected,
                    streamMayHaveIdOrThemeAttrDeclarations(ByteArrayInputStream(bytes), ByteArray(size)),
                    "$name, buffer $size",
                )
            }
        }
    }

    @Test
    fun `sanitize byte scan matches the legacy scan for every buffer size`() {
        for ((name, bytes) in corpus()) {
            val expected = legacyMayNeedSanitize(bytes)
            for (size in bufferSizes) {
                assertEquals(
                    expected,
                    mayNeedSanitize(ByteArrayInputStream(bytes), ByteArray(size)),
                    "$name, buffer $size",
                )
            }
        }
    }

    @Test
    fun `id decision matches the legacy decision for every corpus file`() {
        val processor = processor()
        for ((index, case) in corpus().withIndex()) {
            val file = tempDir.resolve("id-$index.xml").apply { writeBytes(case.second) }
            assertEquals(legacyCanHave(case.second), processor.canHaveIdOrThemeAttrDeclarations(file), case.first)
        }
    }

    @Test
    fun `sanitized strings files are byte identical to the legacy pass`() {
        val cases = corpus()
        val currentRoots = mutableMapOf<String, File>()
        val legacyRoots = mutableMapOf<String, File>()
        cases.forEachIndexed { index, case ->
            currentRoots["pkg$index"] = tempDir.resolve("sanitize-new-$index").also { writeStringsFile(it, case.second) }
            legacyRoots["pkg$index"] = tempDir.resolve("sanitize-old-$index").also { writeStringsFile(it, case.second) }
        }

        StringsXmlSanitizeProcessor({ _, _ -> error("not used") }, currentRoots).process()
        legacyRoots.values.forEach { legacySanitizeFile(it.resolve(STRINGS_PATH)) }

        cases.forEachIndexed { index, case ->
            assertArrayEquals(
                legacyRoots.getValue("pkg$index").resolve(STRINGS_PATH).readBytes(),
                currentRoots.getValue("pkg$index").resolve(STRINGS_PATH).readBytes(),
                case.first,
            )
        }
    }

    @Test
    fun `decisions match the legacy decisions on a decoded resource tree`() {
        // Set ORU_DECODED_RES to a decoded res directory. The files are only read.
        val tree = System.getenv("ORU_DECODED_RES")
        assumeTrue(tree != null, "ORU_DECODED_RES is not set")
        val files = File(tree!!).walkTopDown().filter { it.isFile && it.extension == "xml" }.toList()
        assertTrue(files.isNotEmpty(), "No XML files under $tree")

        val processor = processor()
        var idHits = 0
        for (file in files) {
            val bytes = file.readBytes()
            val expected = legacyCanHave(bytes)
            assertEquals(expected, processor.canHaveIdOrThemeAttrDeclarations(file), file.path)
            if (expected) idHits++
            val expectedSanitize = legacyMayNeedSanitize(bytes)
            assertEquals(expectedSanitize, mayNeedSanitize(ByteArrayInputStream(bytes), ByteArray(7)), file.path)
            assertEquals(expectedSanitize, mayNeedSanitize(ByteArrayInputStream(bytes), ByteArray(64 * 1024)), file.path)
        }
        println("decoded tree ${files.size} xml files, $idHits id hits")
    }

    /** Test inputs: the cases a streaming scan can get wrong, plus seeded random bytes over the pattern alphabet. */
    private fun corpus(): List<Pair<String, ByteArray>> = buildList {
        add("empty" to ByteArray(0))
        for (value in listOf(0x26, 0x2B, 0x3D, 0x3F, 0x23, 0x00, 0xFE, 0xFF, 0xEF, 0xBF, 0xBE, 0x20, 0x22)) {
            add("single byte $value" to byteArrayOf(value.toByte()))
        }
        add("UTF-16 BE BOM only" to byteArrayOf(0xFE.toByte(), 0xFF.toByte()))
        add("UTF-16 LE BOM only" to byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
        add("UTF-16 LE BOM then plain ASCII" to byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "<a/>".toByteArray(Charsets.UTF_16LE))
        add("UTF-8 BOM then id" to byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "+id/".toByteArray())
        add("FE then FF in the middle" to "aþÿb".toByteArray(Charsets.ISO_8859_1))

        for (text in utf8Texts) {
            add("utf8: $text" to text.toByteArray(Charsets.UTF_8))
            // Java's UTF-16 is big endian with a byte order mark.
            add("utf16 BE with BOM: $text" to text.toByteArray(Charsets.UTF_16))
            add("utf16 LE with BOM: $text" to byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE))
            add("utf16 LE without BOM: $text" to text.toByteArray(Charsets.UTF_16LE))
        }

        // Long lines, with the markers placed across the 64 KiB buffer boundary of the production scan.
        val megabyte = 1 shl 20
        add("huge plain line" to "a".repeat(megabyte).toByteArray())
        add("huge line, id marker at end" to ("a".repeat(megabyte) + "+id/").toByteArray())
        add("huge line, marker at 64 KiB" to ("a".repeat(65535) + "+id/x").toByteArray())
        add("huge line, numeric reference across 64 KiB" to ("a".repeat(65535) + "&#65;").toByteArray())
        add("huge line, U+FFFF across 64 KiB" to ("a".repeat(65534) + "￿").toByteArray())
        add("huge whitespace run before quote" to ("=" + " ".repeat(100_000) + "\"?").toByteArray())
        add("huge whitespace run without quote" to ("=" + " ".repeat(100_000) + "x").toByteArray())

        addAll(randomCases(count = 3000, seed = 20260611))
    }

    private val utf8Texts = listOf(
        "<LinearLayout android:id=\"@+id/title\"/>",
        "<string name=\"a\">&#65;</string>",
        "<string name=\"a\">&#x41;</string>",
        "<resources>\u0000</resources>",
        "<item name=\"a\">?someValue</item>",
        "<item name='a' value = '?x'/>",
        "<a b\t=\n \"?c\"/>",
        "<a b=\"x?y\"/>",
        "<a b=\" ?c\"/>",
        "<resources><string name=\"app\">Hi, a=b &amp; c</string></resources>",
        "<a b=\r\"?c\"/>",
        "<a b=\u000B\"?c\"/>",
        "<a b= \"?c\"/>",
        "<a b= \"?c\"/>",
        "<a b=\"?c\"/>\n+id\n/",
        "<a b=\"=\"?\"",
        "=\"=\"?",
        "==\"?",
        "=  '?",
        "+++id/",
        "+i+id/",
        "&&#",
        "&#",
        "&",
        "+id",
        "=\"",
        "\"?",
        "=\"?",
        "￾",
        "￿",
        "😀 &#128512;",
        "﻿<a/>",
        "�\u0001",
        "<string name=\"é\">日本語 +id/x</string>",
    )

    private fun randomCases(count: Int, seed: Int): List<Pair<String, ByteArray>> {
        val random = Random(seed)
        val alphabet = "+id/&#= \t\n\"'?a".map { it.code.toByte() } + listOf(0, 0x01, 0xEF, 0xBF, 0xBE, 0xFE, 0xFF).map { it.toByte() }
        return List(count) { index ->
            "random $index" to ByteArray(random.nextInt(49)) { alphabet[random.nextInt(alphabet.size)] }
        }
    }

    private fun processor() = ResourceIdProcessor(
        get = { tempDir.resolve(it) },
        publicIdManager = PublicXmlManager(
            tempDir.resolve("public.xml").also {
                it.writeText("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<resources package=\"com.example.app\" id=\"0x7f\">\n</resources>")
            },
        ),
        modifiedResResources = emptySet(),
    )

    private fun writeStringsFile(root: File, bytes: ByteArray) {
        root.resolve(STRINGS_PATH).apply { parentFile.mkdirs() }.writeBytes(bytes)
    }

    private companion object {
        const val STRINGS_PATH = "res/values/strings.xml"
    }
}

/** The whole-file id decision before the streaming change. */
internal fun legacyCanHave(bytes: ByteArray): Boolean {
    if (bytes.legacyStartsWithBom(0xFE, 0xFF) || bytes.legacyStartsWithBom(0xFF, 0xFE)) {
        return legacyTextHas(bytes)
    }
    if (!legacyBytesHave(bytes)) return false
    return legacyTextHas(bytes)
}

/** Whether the byte scan of the legacy decision is a hit, with a UTF-16 byte order mark counting as one. */
internal fun legacyIdByteHit(bytes: ByteArray): Boolean =
    bytes.legacyStartsWithBom(0xFE, 0xFF) || bytes.legacyStartsWithBom(0xFF, 0xFE) || legacyBytesHave(bytes)

private fun legacyTextHas(bytes: ByteArray): Boolean {
    val text = String(bytes, Charsets.UTF_8)
    return "+id/" in text || "&#" in text || '\u0000' in text ||
            Regex("""=\s*["']\?""").containsMatchIn(text)
}

private fun ByteArray.legacyStartsWithBom(first: Int, second: Int) =
    size >= 2 && this[0] == first.toByte() && this[1] == second.toByte()

private fun legacyBytesHave(bytes: ByteArray): Boolean {
    for (i in bytes.indices) {
        when (bytes[i]) {
            0.toByte() -> return true
            '+'.code.toByte() -> if (bytes.legacyMatchesAt(i, "+id/")) return true
            '&'.code.toByte() -> if (bytes.legacyMatchesAt(i, "&#")) return true
            '='.code.toByte() -> {
                var j = i + 1
                while (j < bytes.size && (bytes[j] == ' '.code.toByte() || bytes[j] in 0x09..0x0D)) j++
                if (j + 1 < bytes.size && (bytes[j] == '"'.code.toByte() || bytes[j] == '\''.code.toByte()) &&
                    bytes[j + 1] == '?'.code.toByte()
                ) return true
            }
        }
    }
    return false
}

private fun ByteArray.legacyMatchesAt(index: Int, pattern: String): Boolean {
    if (index + pattern.length > size) return false
    for (k in pattern.indices) {
        if (this[index + k] != pattern[k].code.toByte()) return false
    }
    return true
}

/** The sanitize byte prefilter before the streaming change. */
internal fun legacyMayNeedSanitize(bytes: ByteArray): Boolean {
    for (i in bytes.indices) {
        val b = bytes[i].toInt() and 0xFF
        if (b < 0x20 && b != 0x9 && b != 0xA && b != 0xD) return true
        if (b == '&'.code && i + 1 < bytes.size && bytes[i + 1] == '#'.code.toByte()) return true
        if (b == 0xEF && i + 2 < bytes.size && bytes[i + 1] == 0xBF.toByte() &&
            (bytes[i + 2] == 0xBE.toByte() || bytes[i + 2] == 0xBF.toByte())
        ) return true
    }
    return false
}

/** The StringsXmlSanitizeProcessor lambda before the streaming change. */
private fun legacySanitizeFile(file: File) {
    val bytes = file.readBytes()
    if (!legacyMayNeedSanitize(bytes)) return
    val rawXml = String(bytes, Charsets.UTF_8)
    val sanitizedXml = sanitizeXmlText(rawXml)
    if (sanitizedXml !== rawXml) {
        file.writeText(sanitizedXml, Charsets.UTF_8)
    }
}
