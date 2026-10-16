/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.run

import kotlin.test.Test
import kotlin.test.assertEquals

class OruRunFormatTest {

    @Test
    fun `a short stage shows tenths of a second`() {
        assertEquals("0.9 s", OruRunFormat.stageDuration(900))
        assertEquals("0.0 s", OruRunFormat.stageDuration(0))
    }

    @Test
    fun `a stage of a minute or more shows minutes and seconds`() {
        assertEquals("1:05", OruRunFormat.stageDuration(65_000))
    }

    @Test
    fun `elapsed time is minutes and zero-padded seconds`() {
        assertEquals("0:00", OruRunFormat.elapsed(0))
        assertEquals("0:12", OruRunFormat.elapsed(12_400))
        assertEquals("60:05", OruRunFormat.elapsed(3_605_000))
    }

    @Test
    fun `negative durations read as zero`() {
        assertEquals("0:00", OruRunFormat.elapsed(-5_000))
        assertEquals("0.0 s", OruRunFormat.stageDuration(-1))
    }

    @Test
    fun `sizes under a megabyte are kilobytes and above are megabytes with one decimal`() {
        assertEquals("512 KB", OruRunFormat.size(512L * 1024))
        assertEquals("1.5 MB", OruRunFormat.size(3L * 1024 * 1024 / 2))
        assertEquals("336.0 MB", OruRunFormat.size(336L * 1024 * 1024))
    }

    @Test
    fun `megabytes are whole and never negative`() {
        assertEquals("336 MB", OruRunFormat.megabytes(336))
        assertEquals("0 MB", OruRunFormat.megabytes(-1))
    }

    @Test
    fun `a percentage has a percent sign and stays between 0 and 100`() {
        assertEquals("0%", OruRunFormat.percent(0))
        assertEquals("50%", OruRunFormat.percent(50))
        assertEquals("100%", OruRunFormat.percent(100))
        assertEquals("100%", OruRunFormat.percent(140))
        assertEquals("0%", OruRunFormat.percent(-3))
    }
}
