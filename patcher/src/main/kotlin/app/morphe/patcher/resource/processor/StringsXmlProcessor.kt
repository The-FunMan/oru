/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.resource.processor

import app.morphe.patcher.resource.copyAttributes
import app.morphe.patcher.resource.copyNamespaces
import app.morphe.patcher.resource.parseXml
import app.morphe.patcher.resource.writeXml
import app.morphe.patcher.util.FileUtils.safelyMoveTo
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.logging.Logger
import kotlin.math.min

abstract internal class StringsXmlProcessor(
    internal val get: (String, String) -> File,
    internal val packageDirectories: Map<String, File>,
    internal val logString: String
) {
    private val logger = Logger.getLogger(this::class.java.name)

    /** Every strings file of every package. */
    fun stringsFiles(): List<File> = buildList {
        packageDirectories.forEach { (resPackageName, rootDir) ->
            rootDir.resolve("res").listFiles { it.isDirectory }?.forEach { dir ->
                // TODO Strings declared in arrays.xml may also need unescaping of string literals.
                dir.listFiles { it.name == "strings.xml" }?.forEach { file ->
                    val path = "res/${dir.name}/${file.name}"
                    logger.fine { "Processing $path" }
                    add(get(path, resPackageName))
                }
            }
        }
    }

    /**
     * @param files The strings files to process. Anything else in the collection is ignored.
     */
    fun process(files: Collection<File> = stringsFiles()) {
        logger.info(logString)
        val stringsFiles = files.filter { it.name == "strings.xml" && it.isFile }

        // An executor of its own, so the work does not take over the shared common pool
        val executor = Executors.newFixedThreadPool(min(Runtime.getRuntime().availableProcessors(), 4))
        try {
            // invokeAll waits for every file, so none is still being written when a failure is rethrown
            val results = executor.invokeAll(stringsFiles.map { file -> Callable { processFile(file) } })
            results.forEach { result ->
                try {
                    result.get()
                } catch (e: ExecutionException) {
                    throw e.cause ?: e
                }
            }
        } finally {
            executor.shutdown()
        }
    }

    private fun processFile(file: File) {
        // A file whose strings all stay the same is left as it is, which skips the rewrite and the move.
        if (!hasChangedString(file)) return

        val tempFile = File(file.parentFile, file.name + ".tmp")

        file.parseXml { parser ->
            tempFile.writeXml { serializer ->
                serializer.startDocument("UTF-8", true)

                var eventType = parser.eventType
                var insideString = false

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {

                        XmlPullParser.START_TAG -> {
                            val tagName = parser.name

                            serializer.copyNamespaces(parser)
                            serializer.startTag(parser.namespace, tagName)
                            serializer.copyAttributes(parser)

                            if (tagName == "string") {
                                insideString = true
                            }
                        }

                        XmlPullParser.TEXT -> {
                            val text = parser.text
                            if (insideString) {
                                serializer.text(processString(text))
                            } else {
                                serializer.text(text)
                            }
                        }

                        XmlPullParser.END_TAG -> {
                            val tagName = parser.name
                            serializer.endTag(parser.namespace, tagName)

                            if (tagName == "string") {
                                insideString = false
                            }
                        }

                        XmlPullParser.CDSECT -> {
                            serializer.cdsect(parser.text)
                        }

                        XmlPullParser.COMMENT -> {
                            serializer.comment(parser.text)
                        }

                        XmlPullParser.IGNORABLE_WHITESPACE -> {
                            serializer.ignorableWhitespace(parser.text)
                        }
                    }

                    eventType = parser.next()
                }

                serializer.endDocument()
            }
        }

        // Replace original file
        tempFile.safelyMoveTo(file)
    }

    /** Whether [processString] changes the text of any string in [file], which is the only text the rewrite changes. */
    private fun hasChangedString(file: File): Boolean {
        var changed = false
        file.parseXml { parser ->
            var eventType = parser.eventType
            var insideString = false
            while (!changed && eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> if (parser.name == "string") insideString = true
                    XmlPullParser.END_TAG -> if (parser.name == "string") insideString = false
                    XmlPullParser.TEXT -> if (insideString) {
                        val text = parser.text
                        changed = processString(text) != text
                    }
                }
                eventType = parser.next()
            }
        }
        return changed
    }

    abstract fun processString(text: String): String
}
