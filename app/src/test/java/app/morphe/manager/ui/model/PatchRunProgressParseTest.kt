/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.model

import app.morphe.manager.patcher.logger.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PatchRunProgressParseTest {

    @Test
    fun `separate process heap limit line sets the limit and reaches the log`() {
        val parsed = parseRunLine(LogLevel.INFO, "Process heap memory limit: 512MB")

        assertEquals(RunTelemetry.HeapLimit(512), parsed.telemetry)
        assertTrue(parsed.showInLog)
    }

    @Test
    fun `in-process app memory limit line is parsed the same way`() {
        val parsed = parseRunLine(LogLevel.INFO, "App memory limit: 256MB")

        assertEquals(RunTelemetry.HeapLimit(256), parsed.telemetry)
        assertTrue(parsed.showInLog)
    }

    @Test
    fun `heap limit line without a number is not telemetry but still reaches the log`() {
        val parsed = parseRunLine(LogLevel.INFO, "App memory limit: unknown")

        assertNull(parsed.telemetry)
        assertTrue(parsed.showInLog)
    }

    @Test
    fun `done line yields the peak of the average and max pair`() {
        val parsed = parseRunLine(LogLevel.INFO, "Heap after patching: average=180MB max=240MB")

        assertEquals(RunTelemetry.HeapDone(240), parsed.telemetry)
        assertTrue(parsed.showInLog)
    }

    @Test
    fun `heap poll is telemetry and stays out of the log panel`() {
        val parsed = parseRunLine(LogLevel.INFO, "Heap: current=198MB")

        assertEquals(RunTelemetry.HeapSample(198), parsed.telemetry)
        assertFalse(parsed.showInLog)
    }

    @Test
    fun `usage line yields per core loads and io, and stays out of the log panel`() {
        val parsed = parseRunLine(LogLevel.INFO, "Usage: cpu=12,40,7,88 ioRead=1024 ioWrite=256")

        assertEquals(
            RunTelemetry.Usage(coreLoads = listOf(12, 40, 7, 88), ioRead = 1024, ioWrite = 256),
            parsed.telemetry
        )
        assertFalse(parsed.showInLog)
    }

    @Test
    fun `usage after patching line is an ordinary log line`() {
        val parsed = parseRunLine(LogLevel.INFO, "Usage after patching: ioPeak=2048")

        assertNull(parsed.telemetry)
        assertTrue(parsed.showInLog)
    }

    @Test
    fun `trace lines are dropped from the log panel`() {
        val parsed = parseRunLine(LogLevel.TRACE, "Applying patch detail")

        assertNull(parsed.telemetry)
        assertFalse(parsed.showInLog)
    }

    @Test
    fun `trace heap limit still updates the limit but is dropped from the log`() {
        val parsed = parseRunLine(LogLevel.TRACE, "Process heap memory limit: 1024MB")

        assertEquals(RunTelemetry.HeapLimit(1024), parsed.telemetry)
        assertFalse(parsed.showInLog)
    }

    @Test
    fun `warn and error lines reach the log panel`() {
        assertTrue(parseRunLine(LogLevel.WARN, "Potential signature mismatch ignored").showInLog)
        assertTrue(parseRunLine(LogLevel.ERROR, "Patch failed").showInLog)
    }
}
