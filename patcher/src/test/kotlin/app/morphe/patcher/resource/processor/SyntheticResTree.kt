/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.resource.processor

import app.morphe.patcher.resource.fileResourceTypes
import java.io.File

/**
 * A decoded-style `res` tree for the id processor tests and benchmark. Its directories are every file-based type
 * with the usual qualifiers. Its files are known to public.xml, except the `anim_` files, which are not, and it
 * has names with several dots, files without an extension, a nested directory, a root file and an empty type directory.
 */
internal object SyntheticResTree {
    private val qualifiers = listOf(
        "", "-hdpi", "-xhdpi", "-xxhdpi", "-night-v8", "-land", "-v21", "-sw600dp",
        "-mdpi", "-ldrtl-v17", "-nodpi", "-ldpi", "-tvdpi", "-anydpi-v26",
    )
    private val extensions = listOf(".png", ".xml", ".9.png", ".a.b.xml", "", ".webp")

    /** Directories that hold the files public.xml does not know, in addition to the type directories. */
    private const val UNKNOWN_DIRECTORY = "animator"
    private const val EMPTY_DIRECTORY = "mipmap-empty"

    /**
     * Writes `res/` under [root] with [filesPerDirectory] files in each type directory, plus `public.xml` and `ids.xml`
     * under `res/values`. Returns the `res` directory.
     */
    fun write(root: File, filesPerDirectory: Int): File {
        val res = root.resolve("res").apply { mkdirs() }
        val values = res.resolve("values").apply { mkdirs() }
        values.resolve("ids.xml").writeText("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<resources></resources>")
        res.resolve("readme.txt").writeText("not a resource directory entry")

        val publicEntries = mutableListOf<String>()
        val nextIdByType = mutableMapOf<String, Int>()
        fun publicEntry(type: String, name: String) {
            val id = nextIdByType.getOrElse(type) { 0 } + 1
            nextIdByType[type] = id
            publicEntries += "    <public id=\"0x${Integer.toHexString(0x7f000000 + fileResourceTypes.indexOf(type) * 0x10000 + id)}\" " +
                    "type=\"$type\" name=\"$name\"/>"
        }

        val knownKeys = mutableSetOf<Pair<String, String>>()
        for (type in fileResourceTypes) {
            for (qualifier in qualifiers) {
                val dir = res.resolve(type + qualifier).apply { mkdirs() }
                for (i in 0 until filesPerDirectory) {
                    val name = "item_$i" + extensions[i % extensions.size]
                    dir.resolve(name).writeText("<resources/>")
                    if (i == 0) dir.resolve("item_0.xml").writeText("<resources/>")
                    val key = name.substringBeforeLast(".")
                    if (knownKeys.add(type to key)) publicEntry(type, key)
                }
            }
        }

        // Odd entries that a decoded tree can hold: a nested directory, a file with no key of its own.
        res.resolve("drawable").resolve("nested").apply { mkdirs() }.resolve("inner.png").writeText("x")
        res.resolve("drawable").resolve("nested.xml").mkdirs()

        // Unknown to public.xml: the same key in two directories (the first in canonical order wins),
        // and a file that has no key in public.xml anywhere.
        res.resolve("anim-v21").resolve("anim_0.png").apply { parentFile.mkdirs() }.writeText("x")
        val animator = res.resolve(UNKNOWN_DIRECTORY).apply { mkdirs() }
        for (i in 0 until ANIMATOR_FILES) animator.resolve("anim_$i.xml").writeText("<set/>")

        res.resolve(EMPTY_DIRECTORY).mkdirs()

        values.resolve("public.xml").writeText(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<resources package=\"com.example.app\" id=\"0x7f\">\n" +
                publicEntries.joinToString("\n") + "\n</resources>",
        )
        return res
    }

    /** The number of files in [UNKNOWN_DIRECTORY], which public.xml does not list. */
    const val ANIMATOR_FILES = 96
}
