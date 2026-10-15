/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.resource.processor

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.resource.PublicXmlManager
import app.morphe.patcher.resource.CanonicalFileOrder
import app.morphe.patcher.resource.fileResourceTypes
import app.morphe.patcher.resource.forEachAttribute
import app.morphe.patcher.resource.parseXml
import app.morphe.patcher.resource.preOrderTraverse
import app.morphe.patcher.resource.resourceToTagOverrideMapping
import app.morphe.patcher.util.Document
import org.w3c.dom.Node
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.util.logging.Logger

internal class ResourceIdProcessor(
    internal val get: (path: String) -> File,
    internal val publicIdManager: PublicXmlManager,
    internal val modifiedResResources: Set<File>,
    /** Lists the entry names of a directory in any order; ids are assigned in [CanonicalFileOrder] order regardless. */
    private val list: (File) -> Array<String>? = { it.list() },
) {
    private val logger = Logger.getLogger(ResourceIdProcessor::class.java.name)

    /** Reused by every [canHaveIdOrThemeAttrDeclarations] call, which runs on one thread. */
    private val scanBuffer = ByteArray(SCAN_BUFFER_SIZE)

    fun process() {
        logger.info("Generating new resource IDs")

        val resDirectories = children(get("res")) { it.isDirectory } ?: throw PatchException("Resource directory not found")

        // Find all new ID declarations in XML files so we can create a corresponding entry in ids.xml
        // They will get added to public.xml later
        Document(get("res/values/ids.xml")).use { idDoc ->
            val idNode = idDoc.getElementsByTagName("resources").item(0)
                ?: throw IllegalStateException("ids.xml is missing the <resources> root element.")

            CanonicalFileOrder.sort(modifiedResResources)
                .filter { it.exists() && it.extension == "xml" }
                .forEach {
                    if (canHaveIdOrThemeAttrDeclarations(it)) {
                        processIdAndAttrDeclarations(it, idNode)
                    }
                    // Update publicIdManager from values XML files (later, including the newly modified ids.xml)
                    createPublicIdsFromValuesXml(it)
                }
        }

        createPublicIdsFromValuesXml(get("res/values/ids.xml"))

        // Ensure every file-based resource has a public ID. ARSCLib can decode synthetic inline-AAPT
        // resources which are not initially present in public.xml, so this must keep its order:
        // newly added patch resource IDs are assigned after those synthetic entries.
        for (type in fileResourceTypes) {
            val directories = resDirectories.filter { it.name.startsWith(type) }
            for (dir in directories) {
                // An id is never removed, so a name that already has one would be a no-op when its turn came.
                // Those are skipped by name, before any file is stat'd or sorted.
                val unknownNames = list(dir)?.filter { !publicIdManager.idExists(type, it.substringBeforeLast(".")) } ?: continue
                for (file in CanonicalFileOrder.sort(unknownNames.map { File(dir, it) })) {
                    if (file.isFile) publicIdManager.createPublicId(type, file.nameWithoutExtension)
                }
            }
        }
    }

    /** The entries of [dir] that pass [keep], in [CanonicalFileOrder], or null when [dir] cannot be listed. */
    private fun children(dir: File, keep: (File) -> Boolean): List<File>? =
        list(dir)?.map { File(dir, it) }?.filter(keep)?.let(CanonicalFileOrder::sort)

    /**
     * If [file] can have an id declaration or a shorthand theme attribute reference, which
     * [processIdAndAttrDeclarations] rewrites. Most changed files are values files without
     * either, and reading the text is much faster than parsing and writing back the document.
     */
    internal fun canHaveIdOrThemeAttrDeclarations(file: File): Boolean {
        // The byte scan is a miss for most files, and then the file is never read as a whole.
        val mayHaveDeclarations = file.inputStream().use { streamMayHaveIdOrThemeAttrDeclarations(it, scanBuffer) }
        return mayHaveDeclarations && textHasIdOrThemeAttrDeclarations(file.readBytes())
    }

    private fun textHasIdOrThemeAttrDeclarations(bytes: ByteArray): Boolean {
        val text = String(bytes, Charsets.UTF_8)
        // A character reference can spell any of these, and a null character is a sign of
        // a file that is not UTF-8, so such files are always processed.
        return "+id/" in text || "&#" in text || '\u0000' in text ||
                attributeValueStartingWithQuestionMark.containsMatchIn(text)
    }

    private fun processIdAndAttrDeclarations(file: File, idNode: Node): Set<String> {
        val createdIds = mutableSetOf<String>()
        Document(file).use { doc ->
            doc.documentElement.preOrderTraverse { element ->
                val idString = element.getAttribute("android:id")
                if (idString.startsWith("@+id/")) {
                    logger.fine { "Adding $idString to ids.xml" }
                    val idName = idString.substring(5)

                    val item = idNode.ownerDocument.createElement("id")
                    item.setAttribute("name", idName)
                    idNode.appendChild(item)

                    element.setAttribute("android:id", "@id/$idName")
                    createdIds += "@id/$idName"
                }

                // Expand shorthand theme attribute references in all attributes.
                // e.g. ?someValue -> ?attr/someValue, ?android:someValue -> ?android:attr/someValue
                element.forEachAttribute { attr ->
                    val value = attr.nodeValue
                    if (value.startsWith('?') && !value.contains('/')) {
                        val expanded = expandThemeAttrReference(value)
                        if (expanded != value) {
                            logger.fine { "Expanding theme attr reference '${attr.nodeName}'='$value' to '$expanded'" }
                            attr.nodeValue = expanded
                        }
                    }
                }
            }
        }
        return createdIds
    }

    private companion object {
        private const val SCAN_BUFFER_SIZE = 64 * 1024
        private val attributeValueStartingWithQuestionMark = Regex("""=\s*["']\?""")
    }

    private fun expandThemeAttrReference(value: String): String {
        // value starts with '?' and has no '/'
        val afterQuestion = value.substring(1)
        val colonIndex = afterQuestion.indexOf(':')
        return if (colonIndex >= 0) {
            // Has package prefix, e.g. ?android:someValue -> ?android:attr/someValue
            val packagePrefix = afterQuestion.substring(0, colonIndex)
            val attrName = afterQuestion.substring(colonIndex + 1)
            "?$packagePrefix:attr/$attrName"
        } else {
            // No package prefix, e.g. ?someValue -> ?attr/someValue
            "?attr/$afterQuestion"
        }
    }

    private fun createPublicIdsFromValuesXml(file: File) {
        file.parseXml { parser ->
            var eventType = parser.eventType
            var depth = 0
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tagName = parser.name
                        if (depth >= 1) {
                            val idName = parser.getAttributeValue(null, "name")

                            val resolvedTagName = if (tagName == "item") {
                                parser.getAttributeValue(null, "type") ?: tagName
                            } else {
                                tagName
                            }
                            val publicTagName = resourceToTagOverrideMapping[resolvedTagName] ?: resolvedTagName
                            publicIdManager.createPublicId(publicTagName, idName)
                        }
                        depth += 1
                    }
                    XmlPullParser.END_TAG -> {
                        depth -= 1
                    }
                }
                eventType = parser.next()
            }
        }
    }
}

private const val ID_PREFIX = "+id/"

/** The bytes that can start or extend a pattern of [streamMayHaveIdOrThemeAttrDeclarations]. */
private val ID_SCAN_SPECIAL = BooleanArray(256).also { table ->
    for (c in "\u0000+&=") table[c.code] = true
}

/**
 * Streams [input] through [buffer] and reports whether [ResourceIdProcessor.canHaveIdOrThemeAttrDeclarations]
 * needs the text of the file. This scans for the same byte patterns as the whole-file check, with the matching
 * state carried across buffer reads, so the answer does not depend on the buffer size. A UTF-16 byte order mark
 * counts as a hit, because those files are always decided on the text.
 */
internal fun streamMayHaveIdOrThemeAttrDeclarations(input: InputStream, buffer: ByteArray): Boolean {
    var headCount = 0
    var head = 0
    var previous = 0
    var idPrefixMatched = 0
    // 0: no pending '=', 1: after '=' and any whitespace, 2: after the quote that follows it
    var equalsState = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) return false
        for (i in 0 until count) {
            val b = buffer[i].toInt() and 0xFF
            if (headCount == 2 && idPrefixMatched == 0 && equalsState == 0 && previous != '&'.code &&
                !ID_SCAN_SPECIAL[b]
            ) {
                // Most bytes cannot change any pending match, so they only update the previous byte.
                previous = b
                continue
            }
            if (headCount < 2) {
                head = (head shl 8) or b
                headCount++
                if (headCount == 2 && (head == 0xFEFF || head == 0xFFFE)) return true
            }
            if (b == 0) return true
            if (previous == '&'.code && b == '#'.code) return true
            previous = b

            if (b == ID_PREFIX[idPrefixMatched].code) {
                if (++idPrefixMatched == ID_PREFIX.length) return true
            } else {
                idPrefixMatched = if (b == '+'.code) 1 else 0
            }

            val isWhitespace = b == ' '.code || b in 0x09..0x0D
            val next = when (equalsState) {
                2 -> if (b == '?'.code) return true else 0
                1 -> when {
                    isWhitespace -> 1
                    b == '"'.code || b == '\''.code -> 2
                    else -> 0
                }
                else -> 0
            }
            equalsState = if (b == '='.code) 1 else next
        }
    }
}
