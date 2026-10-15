/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.util

import app.morphe.patcher.resource.processor.AaptMacroProcessor
import org.junit.jupiter.api.io.TempDir
import org.xml.sax.SAXParseException
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Resource XML never declares a DOCTYPE, so parsers reject one instead of resolving the external entity it declares.
 */
internal class XmlDoctypeRejectionTest {
    @TempDir
    lateinit var tempDir: File

    private fun externalEntityXml(secret: File) = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE root [<!ENTITY secret SYSTEM "${secret.toURI()}">]>
<root xmlns:aapt="http://schemas.android.com/aapt">&secret;</root>"""

    @Test
    fun `document rejects a doctype that declares an external entity`() {
        val secret = File(tempDir, "secret.txt").apply { writeText("secret") }
        val file = File(tempDir, "doctype.xml").apply { writeText(externalEntityXml(secret)) }

        assertFailsWith<SAXParseException> { Document(file) }
    }

    @Test
    fun `aapt macro processor rejects a doctype that declares an external entity`() {
        val secret = File(tempDir, "secret.txt").apply { writeText("secret") }
        val file = File(tempDir, "doctype-aapt.xml").apply { writeText(externalEntityXml(secret)) }

        assertFailsWith<SAXParseException> {
            AaptMacroProcessor(get = { file }, modifiedResResources = mutableSetOf(file)).process()
        }
    }
}
