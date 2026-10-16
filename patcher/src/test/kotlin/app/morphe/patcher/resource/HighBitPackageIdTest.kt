/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.resource

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Resource ids whose package byte is 0x80 or above, as used by feature split APKs. Such ids are
 * above the signed Int range, so the lookups and the public.xml parser must keep them unsigned.
 */
internal class HighBitPackageIdTest {
    @TempDir
    lateinit var tempDir: File

    @AfterEach
    fun clearCurrent() {
        ResourceIds.current = null
    }

    @Test
    fun `resource ids keep package bytes 0x80 and 0xFF`() {
        val ids = ResourceIds {
            mapOf(
                "id/split_low" to 0x80010001L,
                "id/split_top" to 0xFF010002L,
            )
        }

        assertEquals(0x80010001L, ids[ResourceType.ID, "split_low"])
        assertEquals(0xFF010002L, ids[ResourceType.ID, "split_top"])
        assertEquals(0x80L, ids[ResourceType.ID, "split_low"] ushr 24)
        assertEquals(0xFFL, ids[ResourceType.ID, "split_top"] ushr 24)
        assertTrue(ids.has(ResourceType.ID, "split_top"))
    }

    @Test
    fun `resource id helpers return high bit package ids`() {
        ResourceIds.current = ResourceIds { mapOf("id/split_top" to 0xFF010002L) }

        assertEquals(0xFF010002L, resourceId(ResourceType.ID, "split_top"))
        assertTrue(hasResourceId(ResourceType.ID, "split_top"))
        assertFalse(hasResourceId(ResourceType.ID, "missing"))
    }

    @Test
    fun `public xml keeps package bytes 0x80 and 0xFF unsigned`() {
        val file = publicXml(
            listOf(
                Triple("anim", "split_low", "0x80010001"),
                Triple("anim", "split_top", "0xff010002"),
            )
        )

        PublicXmlManager(file).use { manager ->
            val ids = manager.getDefinedIds()
            assertEquals(0x80010001.toInt(), ids[Pair("anim", "split_low")])
            assertEquals(0xFF010002.toInt(), ids[Pair("anim", "split_top")])
            assertEquals(0xFF010002.toInt(), manager.getHighestResourceIds()["anim"])
        }
    }

    @Test
    fun `new public ids continue above a high bit package id`() {
        val file = publicXml(listOf(Triple("anim", "split_low", "0x80010001")))

        PublicXmlManager(file).use { manager ->
            manager.createPublicId("anim", "added")

            assertEquals(0x80010002.toInt(), manager.getDefinedIds()[Pair("anim", "added")])
        }
    }

    @Test
    fun `public xml rejects signed and oversized ids`() {
        // "-0x" is not hex, so it fails the format check. "+0x" parses as hex, so the explicit
        // signed check rejects it. A value above 0xFFFFFFFF fails the 32-bit range check.
        val malformed = assertFailsWith<IllegalStateException>("-0x80010001 must be rejected") {
            PublicXmlManager(publicXml(listOf(Triple("anim", "bad", "-0x80010001"))))
        }
        assertContains(malformed.message.orEmpty(), "'-0x80010001'")

        val signed = assertFailsWith<IllegalArgumentException>("+0x80010001 must be rejected") {
            PublicXmlManager(publicXml(listOf(Triple("anim", "bad", "+0x80010001"))))
        }
        assertContains(signed.message.orEmpty(), "Signed id")

        val oversized = assertFailsWith<IllegalArgumentException>("0x100000000 must be rejected") {
            PublicXmlManager(publicXml(listOf(Triple("anim", "bad", "0x100000000"))))
        }
        assertContains(oversized.message.orEmpty(), "above 0xFFFFFFFF")
    }

    private fun publicXml(entries: List<Triple<String, String, String>>): File {
        val body = entries.joinToString("\n") { (type, name, id) ->
            "  <public id=\"$id\" type=\"$type\" name=\"$name\" />"
        }
        return tempDir.resolve("public.xml").apply {
            writeText(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<resources package=\"com.example.app\" id=\"0x80\">\n$body\n</resources>",
                Charsets.UTF_8,
            )
        }
    }
}
