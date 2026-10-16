/*
 * Copyright 2026 Morphe.
 * https://github.com/morpheapp/morphe-patcher
 */

package app.morphe.patcher.util

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertTrue

internal class DocumentFactoryReuseTest {
    @TempDir
    lateinit var tempDir: File

    private val xml = """<?xml version="1.0" encoding="UTF-8"?><root><element/></root>"""

    @Test
    fun `factories are created at most once per thread`() {
        val builderCreations = AtomicInteger()
        val transformerCreations = AtomicInteger()
        val builderFactory = XmlFactories.newDocumentBuilderFactory
        val transformerFactory = XmlFactories.newTransformerFactory
        XmlFactories.newDocumentBuilderFactory = {
            builderCreations.incrementAndGet()
            builderFactory()
        }
        XmlFactories.newTransformerFactory = {
            transformerCreations.incrementAndGet()
            transformerFactory()
        }

        try {
            val documentsPerThread = 5
            val threads = (1..2).map { index ->
                Thread {
                    repeat(documentsPerThread) { document ->
                        val file = File(tempDir, "thread-$index-$document.xml").apply { writeText(xml) }
                        Document(file).close()
                    }
                }
            }
            threads.forEach { it.start() }
            threads.forEach { it.join() }
        } finally {
            XmlFactories.newDocumentBuilderFactory = builderFactory
            XmlFactories.newTransformerFactory = transformerFactory
        }

        assertTrue(builderCreations.get() in 1..2, "document builder factories created: ${builderCreations.get()}")
        assertTrue(transformerCreations.get() in 1..2, "transformer factories created: ${transformerCreations.get()}")
    }
}
