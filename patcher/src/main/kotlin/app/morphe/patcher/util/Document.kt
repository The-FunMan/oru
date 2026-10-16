package app.morphe.patcher.util

import app.morphe.patcher.environment.EnvironmentUtils.isAndroidEnvironment
import app.morphe.patcher.resource.utf8Writer
import org.w3c.dom.Document
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Creates the XML factories. The creators are replaceable so tests can count factory creations.
 */
internal object XmlFactories {
    internal var newDocumentBuilderFactory: () -> DocumentBuilderFactory = {
        DocumentBuilderFactory.newInstance().apply {
            // Resource XML never has a DOCTYPE. Parsers that lack the feature keep their defaults.
            runCatching { setFeature(DISALLOW_DOCTYPE_FEATURE, true) }
        }
    }
    internal var newTransformerFactory: () -> TransformerFactory = TransformerFactory::newInstance

    // Factory configuration is not guaranteed to be thread-safe, so each thread keeps its own factories.
    private val documentBuilderFactories = ThreadLocal.withInitial { newDocumentBuilderFactory() }
    private val transformerFactories = ThreadLocal.withInitial { newTransformerFactory() }

    fun newDocumentBuilder() = documentBuilderFactories.get().newDocumentBuilder()

    fun newTransformer() = transformerFactories.get().newTransformer()

    private const val DISALLOW_DOCTYPE_FEATURE = "http://apache.org/xml/features/disallow-doctype-decl"
}

class Document internal constructor(
    inputStream: InputStream,
) : Document by XmlFactories.newDocumentBuilder().parse(inputStream), Closeable {
    private var file: File? = null

    // The serialized form when the file was opened. Changes made through the DOM cannot be observed,
    // so close compares against this to tell whether the document was changed.
    private var openedContent: String? = null

    init {
        normalize()
    }

    internal constructor(file: File) : this(file.inputStream()) {
        this.file = file
        openedContent = serialize()
        synchronized(readerCount) {
            readerCount.merge(file, 1, Int::plus)
        }
    }

    override fun close() {
        file?.let {
            synchronized(readerCount) {
                if (readerCount[it]!! > 1) {
                    throw IllegalStateException(
                        "Two or more instances are currently reading $it." +
                            "To be able to close this instance, no other instances may be reading $it at the same time.",
                    )
                } else {
                    readerCount.remove(it)
                }
            }

            val content = serialize()
            // A document that was only read is left as it is on disk
            if (content == openedContent) return

            if (isAndroidEnvironment) {
                it.utf8Writer().use { writer -> writer.write(content) }
            } else {
                it.writeText(content)
            }
        }
    }

    private fun serialize(): String {
        val transformer = XmlFactories.newTransformer()
        // Set to UTF-16 to prevent surrogate pairs from being escaped to invalid numeric character references, but save as UTF-8.
        if (isAndroidEnvironment) {
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-16")
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        }
        return StringWriter().also { writer ->
            transformer.transform(DOMSource(this), StreamResult(writer))
        }.toString()
    }

    private companion object {
        private val readerCount = mutableMapOf<File, Int>()
    }
}
