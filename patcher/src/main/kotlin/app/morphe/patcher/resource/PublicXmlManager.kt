/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.resource

import app.morphe.patcher.util.FileUtils.safelyMoveTo
import org.xmlpull.v1.XmlPullParser
import java.io.Closeable
import java.io.File
import java.util.logging.Logger

class PublicXmlManager(
    private val publicDocPath: File
) : Closeable {
    private val logger = Logger.getLogger(this::class.java.name)
    private val resourceIds = mutableMapOf<String, Int>()
    private val definedIdsByType = mutableMapOf<String, MutableMap<String, Int>>()
    private lateinit var packageName: String
    private lateinit var packageId: String

    init {
        if (!publicDocPath.exists()) {
            throw IllegalArgumentException("File does not exist at path: ${publicDocPath.absolutePath}")
        }
        readPublicXml()
    }

    private fun readPublicXml() {
        publicDocPath.parseXml { parser ->
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "resources") {
                            val attributes = parser.getAttributesAsMap()
                            packageName = attributes["package"] ?: throw IllegalStateException("Missing 'package' attribute in <resources> element.")
                            packageId = attributes["id"] ?: throw IllegalStateException("Missing 'id' attribute in <resources> element.")
                        }
                        else if (parser.name == "public") {
                            val attributes = parser.getAttributesAsMap()
                            val idString = attributes["id"] ?: throw IllegalStateException("Missing 'id' attribute in <public> element.")
                            val typeString = attributes["type"] ?: throw IllegalStateException("Missing 'type' attribute in <public> element.")
                            val nameString = attributes["name"] ?: throw IllegalStateException("Missing 'name' attribute in <public> element.")

                            val cleanId = idString.removePrefix("0x").removePrefix("0X")
                            require(!cleanId.startsWith('+')) { "Signed id in <public> element: '$idString'." }
                            val value = cleanId.toULongOrNull(16) ?: throw IllegalStateException(
                                "Malformed id in <public> element: '$idString' (expected format like '0x1234')."
                            )
                            require(value <= 0xFFFFFFFFuL) { "Id above 0xFFFFFFFF in <public> element: '$idString'." }
                            val id = value.toInt()

                            if (id.toUInt() > resourceIds.getOrElse(typeString) { 0 }.toUInt()) {
                                resourceIds[typeString] = id
                            }
                            // Need to add type because it is possible to have multiple resources with the same name but different types.
                            definedIdsByType.getOrPut(typeString) { mutableMapOf() }[nameString] = id
                        }
                    }
                }
                eventType = parser.next()
            }
        }
    }

    fun idExists(type: String, name: String): Boolean {
        return definedIdsByType[type]?.containsKey(name) == true
    }

    fun getHighestResourceIds(): Map<String, Int> {
        return resourceIds
    }

    fun getDefinedIds(): Map<Pair<String, String>, Int> {
        return buildMap {
            definedIdsByType.forEach { (type, ids) ->
                ids.forEach { (name, id) -> put(type to name, id) }
            }
        }
    }

    fun createPublicId(type: String, name: String?) {
        if (name == null || idExists(type, name)) {
            return
        }

        logger.fine { "Adding @$type/$name to public.xml" }

        val resourceId = resourceIds.getOrElse(type) { 0 } + 1
        resourceIds[type] = resourceId
        definedIdsByType.getOrPut(type) { mutableMapOf() }[name] = resourceId
    }

    internal fun changePackageName(packageName: String) {
        this.packageName = packageName
    }

    internal fun changePackageId(packageId: Int) {
        this.packageId = "0x${packageId.toString(16)}"
    }

    override fun close() {
        val tempFile = File(publicDocPath.parentFile, publicDocPath.name + ".tmp")
        tempFile.writeXml { serializer ->
            serializer.startDocument("UTF-8", true)

            // Write <resources> start tag with package and id attributes
            serializer.startTag(null, "resources")
            serializer.attribute(null, "package", packageName)
            serializer.attribute(null, "id", packageId)

            // Write <public> tags for each resource ID
            definedIdsByType.flatMap { (type, ids) ->
                ids.map { (name, id) -> Triple(type, name, id) }
            }.sortedBy { it.third }.forEach { (type, name, id) ->
                serializer.startTag(null, "public")
                serializer.attribute(null, "id", "0x${id.toUInt().toString(16)}")
                serializer.attribute(null, "type", type)
                serializer.attribute(null, "name", name)
                serializer.endTag(null, "public")
            }

            // Write </resources> end tag
            serializer.endTag(null, "resources")
            serializer.endDocument()
        }

        // Replace original file
        tempFile.safelyMoveTo(publicDocPath)
    }
}

/**
 * The one order in which resource files are visited. `File.listFiles` returns entries in whatever
 * order the filesystem gives, so anything that decides change order or public ids goes through
 * here. Files are ordered by path, compared as unsigned UTF-8 bytes, which does not depend on locale.
 */
internal object CanonicalFileOrder {
    /** Sorts [files] by their path. */
    fun sort(files: Collection<File>): List<File> =
        files.map { it to it.path.toByteArray(Charsets.UTF_8) }
            .sortedWith { a, b -> java.util.Arrays.compareUnsigned(a.second, b.second) }
            .map { it.first }

    /**
     * Every regular file under [root], in [sort] order. [list] returns the entries of a directory,
     * or null when it cannot be read, and is replaceable so tests can supply a listing in any order.
     */
    fun walkFiles(root: File, list: (File) -> Array<File>? = { it.listFiles() }): List<File> {
        val files = mutableListOf<File>()
        fun visit(file: File) {
            when {
                file.isDirectory -> list(file)?.forEach(::visit)
                file.isFile -> files += file
            }
        }
        visit(root)
        return sort(files)
    }
}
