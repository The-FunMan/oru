/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.run

import kotlin.test.Test
import kotlin.test.assertEquals

class OruUsageFormatTest {

    private val figure = ' '

    @Test
    fun `heap is padded so values of different sizes take the same width`() {
        val widths = listOf(0, 7, 180, 1536, 9999).map { OruUsageFormat.heapMb(it).length }

        assertEquals(setOf(widths.first()), widths.toSet())
        assertEquals("${figure}${figure}${figure}0 MB", OruUsageFormat.heapMb(0))
        assertEquals("${figure}180 MB", OruUsageFormat.heapMb(180))
        assertEquals("1536 MB", OruUsageFormat.heapMb(1536))
    }

    @Test
    fun `negative heap is shown as zero`() {
        assertEquals(OruUsageFormat.heapMb(0), OruUsageFormat.heapMb(-5))
    }

    @Test
    fun `cpu percent is clamped and padded to three places`() {
        assertEquals("${figure}${figure}0%", OruUsageFormat.cpuPercent(0))
        assertEquals("${figure}42%", OruUsageFormat.cpuPercent(42))
        assertEquals("100%", OruUsageFormat.cpuPercent(100))
        assertEquals("100%", OruUsageFormat.cpuPercent(250))
        assertEquals("${figure}${figure}0%", OruUsageFormat.cpuPercent(-3))
    }

    @Test
    fun `io below one megabyte per second is shown in KB per second`() {
        assertEquals("${figure}${figure}${figure}0 KB/s", OruUsageFormat.ioRate(0))
        assertEquals("${figure}512 KB/s", OruUsageFormat.ioRate(512))
        assertEquals("1023 KB/s", OruUsageFormat.ioRate(1023))
    }

    @Test
    fun `io from one megabyte per second is shown in MB per second with one decimal`() {
        assertEquals("${figure}1.0 MB/s", OruUsageFormat.ioRate(1024))
        assertEquals("${figure}1.5 MB/s", OruUsageFormat.ioRate(1536))
        assertEquals("10.0 MB/s", OruUsageFormat.ioRate(10 * 1024))
    }

    @Test
    fun `io uses a dot as decimal separator whatever the locale`() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals("${figure}1.5 MB/s", OruUsageFormat.ioRate(1536))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }
}
