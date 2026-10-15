/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.resource.processor

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.logging.Logger

internal class StringsXmlSanitizeProcessor(
    internal val get: (String, String) -> File,
    internal val packageDirectories: Map<String, File>,
) {
    private val logger = Logger.getLogger(StringsXmlSanitizeProcessor::class.java.name)

    private val scanBuffers = ThreadLocal.withInitial { ByteArray(SCAN_BUFFER_SIZE) }

    fun process() {
        logger.info("Sanitizing unpatched strings")

        val stringFiles = buildList {
            packageDirectories.forEach { (_, rootDir) ->
                rootDir.resolve("res").listFiles { it.isDirectory }?.forEach { dir ->
                    dir.listFiles { it.name == "strings.xml" }?.forEach { file ->
                        add(file)
                    }
                }
            }
        }

        stringFiles.parallelStream().forEach { file ->
            // Most files need no sanitizing, so they are only streamed through a per-thread buffer.
            val needsSanitize = file.inputStream().use { mayNeedSanitize(it, scanBuffers.get()) }
            if (!needsSanitize) return@forEach

            val rawXml = String(file.readBytes(), Charsets.UTF_8)
            val sanitizedXml = sanitizeXmlText(rawXml)
            if (sanitizedXml !== rawXml) {
                file.writeText(sanitizedXml, Charsets.UTF_8)
            }
        }
    }
}

private const val SCAN_BUFFER_SIZE = 64 * 1024

/**
 * Byte-level check that never misses a file [sanitizeXmlText] would change, so most files are
 * skipped without decoding them. A false positive only costs the text check. The bytes are streamed
 * through [buffer], with the state of a multi-byte pattern carried across reads.
 */
internal fun mayNeedSanitize(input: InputStream, buffer: ByteArray): Boolean {
    var previous = 0
    // 1 after the lead byte of U+FFFE and U+FFFF, 2 after its second byte
    var nonCharacterState = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) return false
        for (i in 0 until count) {
            val b = buffer[i].toInt() and 0xFF
            // Control characters other than tab, line feed and carriage return are invalid in XML 1.0.
            if (b < 0x20 && b != 0x9 && b != 0xA && b != 0xD) return true
            // A numeric character reference, which may be out of range.
            if (previous == '&'.code && b == '#'.code) return true
            // U+FFFE and U+FFFF, the only non-characters that decoded UTF-8 can produce from valid bytes.
            if (nonCharacterState == 2 && (b == 0xBE || b == 0xBF)) return true
            nonCharacterState = when {
                b == 0xEF -> 1
                nonCharacterState == 1 && b == 0xBF -> 2
                else -> 0
            }
            previous = b
        }
    }
}

internal fun mayNeedSanitize(bytes: ByteArray): Boolean =
    mayNeedSanitize(ByteArrayInputStream(bytes), ByteArray(SCAN_BUFFER_SIZE))


/**
 * Clean both forms of invalid XML characters:
 * 1. Remove invalid numeric character references (e.g., &#65535;), which would
 *    otherwise cause the XML parser to fail before the document can be loaded.
 * 2. Remove any literal invalid Unicode characters that may appear directly in
 *    the file (e.g., U+FFFF), ensuring the resulting text always conforms to
 *    XML 1.0's allowed character ranges.
 */
internal fun sanitizeXmlText(input: String): String {
    fun isValidXmlChar(code: Int): Boolean =
        code == 0x9 ||
                code == 0xA ||
                code == 0xD ||
                (code in 0x20..0xD7FF) ||
                (code in 0xE000..0xFFFD) ||
                (code in 0x10000..0x10FFFF)

    // Most strings.xml files are valid. Scan once and allocate only if an invalid literal or numeric
    // reference is encountered. Code-point iteration also preserves valid supplementary Unicode characters.
    var output: StringBuilder? = null
    var index = 0
    while (index < input.length) {
        if (input[index] == '&' && index + 2 < input.length && input[index + 1] == '#') {
            val semicolon = input.indexOf(';', index + 2)
            if (semicolon >= 0) {
                val digitsStart = index + 2
                var numeric = digitsStart < semicolon
                var digitIndex = digitsStart
                while (numeric && digitIndex < semicolon) {
                    numeric = input[digitIndex].isDigit()
                    digitIndex++
                }
                if (numeric) {
                    val value = input.substring(digitsStart, semicolon).toLongOrNull()
                    if (value == null || value > Int.MAX_VALUE || !isValidXmlChar(value.toInt())) {
                        if (output == null) {
                            output = StringBuilder(input.length).append(input, 0, index)
                        }
                        index = semicolon + 1
                        continue
                    }
                }
            }
        }

        val codePoint = Character.codePointAt(input, index)
        val charCount = Character.charCount(codePoint)
        if (isValidXmlChar(codePoint)) {
            output?.append(input, index, index + charCount)
        } else if (output == null) {
            output = StringBuilder(input.length).append(input, 0, index)
        }
        index += charCount
    }

    return output?.toString() ?: input
}
