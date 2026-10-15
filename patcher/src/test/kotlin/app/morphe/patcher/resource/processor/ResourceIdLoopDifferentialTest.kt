/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.resource.processor

import app.morphe.patcher.resource.CanonicalFileOrder
import app.morphe.patcher.resource.PublicXmlManager
import app.morphe.patcher.resource.fileResourceTypes
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The file-based id loop of [ResourceIdProcessor] against the loop as it was before it skipped known names by name:
 * same ids, and the same public.xml bytes, on a tree with files that public.xml does not know, files a patch added
 * and deleted, names with several dots, and an empty type directory.
 */
internal class ResourceIdLoopDifferentialTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `new loop assigns the same ids as the old loop`() {
        val res = treeWithPatchChanges()
        val (oldIds, oldXml) = runOldLoop(res)
        val (newIds, newXml) = runProcessor(res)

        assertEquals(oldIds, newIds)
        assertContentEquals(oldXml, newXml)
        // The comparison must cover the cases it is about, not just two empty results.
        for (key in listOf("x.9", "a.b.c", "noext", "slide", "anim_5", "anim_0", "anim_1")) {
            assertTrue(newIds.keys.any { it.second == key }, "no id for $key")
        }
    }

    @Test
    fun `directory listing order does not change the ids`() {
        val res = treeWithPatchChanges()
        val (defaultIds, defaultXml) = runProcessor(res)
        val (reversedIds, reversedXml) = runProcessor(res) { dir -> dir.list()?.reversedArray() }

        assertEquals(defaultIds, reversedIds)
        assertContentEquals(defaultXml, reversedXml)
    }

    private fun treeWithPatchChanges(): File {
        val res = SyntheticResTree.write(tempDir.resolve("tree-${System.nanoTime()}"), filesPerDirectory = 12)
        // Added by a patch: a new key, a key that another file of the same type already has, odd names, a new directory.
        res.resolve("drawable-hdpi/patch_added.png").writeText("x")
        res.resolve("drawable/x.9.png").writeText("x")
        res.resolve("raw/a.b.c.xml").writeText("<raw/>")
        res.resolve("raw/.hidden").writeText("x")
        res.resolve("layout/noext").writeText("<layout/>")
        res.resolve("anim-v21/anim_5.png").writeText("x")
        res.resolve("interpolator-v21").mkdirs()
        res.resolve("interpolator-v21/slide.xml").writeText("<interpolator/>")
        // Deleted by a patch: the file that gave anim_0 its place in the canonical order, and one of a known key.
        res.resolve("anim-v21/anim_0.png").delete()
        res.resolve("drawable/item_1.xml").delete()
        return res
    }

    private fun runOldLoop(res: File): Pair<Map<Pair<String, String>, Int>, ByteArray> {
        val publicXml = copyOfPublicXml(res, "old")
        val ids = PublicXmlManager(publicXml).use { manager ->
            val resDirectories = CanonicalFileOrder.sort(res.listFiles()!!.filter { it.isDirectory })
            for (type in fileResourceTypes) {
                for (dir in resDirectories.filter { it.name.startsWith(type) }) {
                    val files = CanonicalFileOrder.sort(dir.listFiles()!!.filter { it.isFile })
                    for (file in files) manager.createPublicId(type, file.nameWithoutExtension)
                }
            }
            manager.getDefinedIds()
        }
        return ids to publicXml.readBytes()
    }

    private fun runProcessor(
        res: File,
        list: ((File) -> Array<String>?)? = null,
    ): Pair<Map<Pair<String, String>, Int>, ByteArray> {
        val publicXml = copyOfPublicXml(res, "new-${System.nanoTime()}")
        val root = res.parentFile
        val ids = PublicXmlManager(publicXml).use { manager ->
            val processor = if (list == null) {
                ResourceIdProcessor(get = { root.resolve(it) }, publicIdManager = manager, modifiedResResources = emptySet())
            } else {
                ResourceIdProcessor(
                    get = { root.resolve(it) },
                    publicIdManager = manager,
                    modifiedResResources = emptySet(),
                    list = list,
                )
            }
            processor.process()
            manager.getDefinedIds()
        }
        return ids to publicXml.readBytes()
    }

    /** A private copy of the tree's public.xml, so each run starts from the same ids and writes its own result. */
    private fun copyOfPublicXml(res: File, name: String): File =
        tempDir.resolve("$name-public.xml").apply { writeBytes(res.resolve("values/public.xml").readBytes()) }
}
