/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.copy

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Locates a file the copy tests read. The path is relative to the app module, and the tests can run
 * from the module or from the project root.
 */
internal fun resolve(relative: String): File {
    val candidates = listOf(File(relative), File("app/$relative"))
    return candidates.firstOrNull { it.exists() }
        ?: error("Missing $relative (working directory ${File(".").absoluteFile})")
}

/**
 * Name and text for each `<string>` and each `<plurals>` (its items joined by a newline),
 * read from a resources file with a real XML parser.
 */
internal fun readEntries(file: File): List<Pair<String, String>> {
    val factory = DocumentBuilderFactory.newInstance()
    val doc = factory.newDocumentBuilder().parse(file.inputStream().buffered())
    val root = doc.documentElement
    val out = mutableListOf<Pair<String, String>>()
    val children = root.childNodes
    for (i in 0 until children.length) {
        val node = children.item(i) as? Element ?: continue
        val name = node.getAttribute("name")
        when (node.tagName) {
            "string" -> out += name to node.textContent
            "plurals" -> {
                val items = node.getElementsByTagName("item")
                val text = (0 until items.length).joinToString("\n") { items.item(it).textContent }
                out += name to text
            }
        }
    }
    return out
}
