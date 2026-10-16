/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhaseProfilerJsonTest {
    @Test
    fun `run line has the documented shape and escapes names`() {
        val line = PhaseProfiler.encodeRun(
            peakHeapBytes = 635L shl 20,
            forcedGc = true,
            phases = listOf(PhaseProfiler.Phase("resource decode", 1_000, 900, 22L shl 20, 3, 40, 5, 6, 4)),
            patches = listOf(PhaseProfiler.PatchTrace(0, "Hide \"ads\"", 5, 2, 1)),
            scans = listOf(PhaseProfiler.ScanRecord("match Foo\\Bar", 2, 30, 7)),
            counters = mapOf("b" to 2L, "a" to 1L),
            timers = mapOf("classdef.methods.firstTouch.ns" to 9L),
        )

        val root = Json.parseToJsonElement(line).jsonObject
        assertEquals(listOf("peakHeapMb", "forcedGc", "phases", "patches", "fingerprintScans", "counters", "timers"), root.keys.toList())
        assertEquals(635, root.getValue("peakHeapMb").jsonPrimitive.int)
        assertTrue(root.getValue("forcedGc").jsonPrimitive.boolean)

        val phase = root.getValue("phases").jsonArray.single().jsonObject
        assertEquals(
            listOf("name", "wallNs", "phaseNs", "liveMb", "gcCount", "gcMs", "blockingGcCount", "blockingGcMs", "threads"),
            phase.keys.toList(),
        )
        assertEquals("resource decode", phase.getValue("name").jsonPrimitive.content)
        assertEquals(1_000, phase.getValue("wallNs").jsonPrimitive.int)
        assertEquals(900, phase.getValue("phaseNs").jsonPrimitive.int)
        assertEquals(22, phase.getValue("liveMb").jsonPrimitive.int)
        assertEquals(3, phase.getValue("gcCount").jsonPrimitive.int)
        assertEquals(40, phase.getValue("gcMs").jsonPrimitive.int)
        assertEquals(5, phase.getValue("blockingGcCount").jsonPrimitive.int)
        assertEquals(6, phase.getValue("blockingGcMs").jsonPrimitive.int)
        assertEquals(4, phase.getValue("threads").jsonPrimitive.int)

        val patch = root.getValue("patches").jsonArray.single().jsonObject
        assertEquals(0, patch.getValue("index").jsonPrimitive.int)
        assertEquals("Hide \"ads\"", patch.getValue("name").jsonPrimitive.content)
        assertEquals(2, patch.getValue("classesMutable").jsonPrimitive.int)
        assertEquals(1, patch.getValue("classesAdded").jsonPrimitive.int)

        val scan = root.getValue("fingerprintScans").jsonArray.single().jsonObject
        assertEquals("match Foo\\Bar", scan.getValue("key").jsonPrimitive.content)
        assertEquals(2, scan.getValue("calls").jsonPrimitive.int)
        assertEquals(30, scan.getValue("visits").jsonPrimitive.int)
        assertEquals(7, scan.getValue("ns").jsonPrimitive.int)

        val counters = root.getValue("counters").jsonObject
        assertEquals(listOf("a", "b"), counters.keys.toList())
        assertEquals(1, counters.getValue("a").jsonPrimitive.int)

        assertEquals(9, root.getValue("timers").jsonObject.getValue("classdef.methods.firstTouch.ns").jsonPrimitive.int)
    }

    @Test
    fun `android runs do not force a gc and desktop runs do`() {
        assertFalse(PhaseProfiler.forcedGcOnMark(android = true))
        assertTrue(PhaseProfiler.forcedGcOnMark(android = false))

        val line = PhaseProfiler.encodeRun(0, forcedGc = false, emptyList(), emptyList(), emptyList(), emptyMap(), emptyMap())
        assertFalse(Json.parseToJsonElement(line).jsonObject.getValue("forcedGc").jsonPrimitive.boolean)
    }

    @Test
    fun `empty run is still a complete JSON object`() {
        val line = PhaseProfiler.encodeRun(0, forcedGc = true, emptyList(), emptyList(), emptyList(), emptyMap(), emptyMap())

        val root = Json.parseToJsonElement(line).jsonObject
        assertEquals(0, root.getValue("phases").jsonArray.size)
        assertEquals(0, root.getValue("patches").jsonArray.size)
        assertEquals(0, root.getValue("fingerprintScans").jsonArray.size)
        assertEquals(0, root.getValue("counters").jsonObject.size)
        assertEquals(0, root.getValue("timers").jsonObject.size)
    }
}
