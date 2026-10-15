/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.resource

import app.morphe.patcher.resource.processor.ResourceIdProcessor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals

internal class CanonicalFileOrderTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `sort orders paths byte-wise over UTF-8`() {
        // Upper case sorts before lower case and a two-byte UTF-8 letter sorts after every ASCII letter.
        val sorted = CanonicalFileOrder.sort(listOf("é", "z", "b", "B", "a").map { File("res", it) })

        assertEquals(listOf("B", "a", "b", "z", "é"), sorted.map { it.name })
    }

    @Test
    fun `walkFiles visits the same files in the same order whatever order the listing gives`() {
        val root = tempDir.resolve("res")
        listOf(
            "drawable/b.xml",
            "drawable/a.xml",
            "layout-v21/c.xml",
            "layout/d.xml",
            "values/strings.xml",
        ).forEach { path -> root.resolve(path).apply { parentFile.mkdirs() }.writeText("<x/>") }

        val natural = CanonicalFileOrder.walkFiles(root)
        val reversed = CanonicalFileOrder.walkFiles(root) { dir -> dir.listFiles()?.reversedArray() }

        val expected = listOf(
            "drawable/a.xml",
            "drawable/b.xml",
            // '-' sorts before '/', so the layout-v21 directory comes before the layout files.
            "layout-v21/c.xml",
            "layout/d.xml",
            "values/strings.xml",
        )
        assertEquals(expected, natural.map { it.relativeTo(root).invariantSeparatorsPath })
        assertEquals(natural, reversed)
    }

    @Test
    fun `public ids do not depend on the order the directory was listed in`() {
        val forward = publicIdsFromListing(reversed = false)
        val reversed = publicIdsFromListing(reversed = true)

        assertEquals(forward, reversed)
        assertEquals(1, forward.getValue("drawable" to "a"))
        assertEquals(2, forward.getValue("drawable" to "b"))
        assertEquals(1, forward.getValue("layout" to "c"))
        assertEquals(2, forward.getValue("layout" to "d"))
    }

    private fun publicIdsFromListing(reversed: Boolean): Map<Pair<String, String>, Int> {
        val root = tempDir.resolve(if (reversed) "reversed" else "forward")
        val values = root.resolve("res/values").apply { mkdirs() }
        values.resolve("public.xml").writeText(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<resources package=\"com.example.app\" id=\"0x7f\">\n</resources>",
        )
        values.resolve("ids.xml").writeText("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<resources></resources>")
        listOf("drawable/a.xml", "drawable/b.xml", "layout/c.xml", "layout/d.xml").forEach { path ->
            root.resolve("res/$path").apply { parentFile.mkdirs() }.writeText("<vector/>")
        }

        return PublicXmlManager(values.resolve("public.xml")).use { publicXmlManager ->
            ResourceIdProcessor(
                get = { path -> root.resolve(path) },
                publicIdManager = publicXmlManager,
                modifiedResResources = emptySet(),
                list = { dir -> dir.list()?.let { if (reversed) it.reversedArray() else it } },
            ).process()

            publicXmlManager.getDefinedIds()
        }
    }
}
