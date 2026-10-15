/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import app.morphe.manager.ui.oru.theme.OruSurfaceFill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OruComponentLogicTest {

    @Test
    fun `chip tone maps to its container, and warn never uses the error container`() {
        assertEquals(OruContainerRole.Neutral, oruContainerRole(OruTone.Neutral))
        assertEquals(OruContainerRole.Ok, oruContainerRole(OruTone.Ok))
        assertEquals(OruContainerRole.Warn, oruContainerRole(OruTone.Warn))
        assertEquals(OruContainerRole.Error, oruContainerRole(OruTone.Error))
        assertFalse(oruContainerRole(OruTone.Warn) == OruContainerRole.Error)
    }

    @Test
    fun `wave is flat at rest, flat when complete and flat when reduced`() {
        assertEquals(0f, oruWaveAmplitude(0f, reduced = false))
        assertEquals(0f, oruWaveAmplitude(1f, reduced = false))
        assertEquals(0f, oruWaveAmplitude(0.5f, reduced = true))
        assertEquals(0f, oruWaveAmplitude(Float.NaN, reduced = false))
    }

    @Test
    fun `wave ramps over the first sixth and then holds full height`() {
        assertEquals(0.5f, oruWaveAmplitude(1f / 12f, reduced = false), 0.0001f)
        assertEquals(1f, oruWaveAmplitude(1f / 6f, reduced = false), 0.0001f)
        assertEquals(1f, oruWaveAmplitude(0.5f, reduced = false), 0.0001f)
    }

    @Test
    fun `stage states follow the current stage`() {
        assertEquals(
            listOf(OruStageState.Done, OruStageState.Now, OruStageState.Todo),
            oruStageStates(count = 3, currentIndex = 1, failed = false),
        )
        assertEquals(
            listOf(OruStageState.Done, OruStageState.Error, OruStageState.Todo),
            oruStageStates(count = 3, currentIndex = 1, failed = true),
        )
        assertEquals(
            listOf(OruStageState.Todo, OruStageState.Todo),
            oruStageStates(count = 2, currentIndex = -1, failed = false),
        )
        assertEquals(
            listOf(OruStageState.Done, OruStageState.Done),
            oruStageStates(count = 2, currentIndex = 2, failed = false),
        )
        assertEquals(emptyList(), oruStageStates(count = 0, currentIndex = 0, failed = false))
    }

    @Test
    fun `only the running stage pulses and only a finished stage with lines toggles`() {
        assertTrue(oruStagePulses(OruStageState.Now))
        assertFalse(oruStagePulses(OruStageState.Done))
        assertFalse(oruStagePulses(OruStageState.Error))

        assertTrue(oruStageCanToggle(OruStageState.Done, hasLines = true))
        assertFalse(oruStageCanToggle(OruStageState.Done, hasLines = false))
        assertFalse(oruStageCanToggle(OruStageState.Now, hasLines = true))
    }

    @Test
    fun `running stage shows its last three lines, others show all`() {
        val lines = (1..5).map { OruStageLine("line $it") }
        assertEquals(lines.takeLast(3), oruVisibleStageLines(lines, OruStageState.Now))
        assertEquals(lines, oruVisibleStageLines(lines, OruStageState.Done))
    }

    @Test
    fun `row control maps to its kind and value`() {
        assertEquals(OruRowControlKind.Switch, oruRowControlKind(OruRowControl.Switch(true)))
        assertEquals(OruRowControlKind.Value, oruRowControlKind(OruRowControl.Value("On")))
        assertEquals(OruRowControlKind.Chevron, oruRowControlKind(OruRowControl.Chevron))
        assertEquals(OruRowControlKind.None, oruRowControlKind(OruRowControl.None))

        assertEquals("On", oruRowControlValue(OruRowControl.Value("On")))
        assertEquals(null, oruRowControlValue(OruRowControl.Switch(false)))
    }

    @Test
    fun `hero glyph and morph follow the state`() {
        assertEquals(OruHeroGlyph.Tile, oruHeroGlyph(OruHeroState.Ready))
        assertEquals(null, oruHeroGlyph(OruHeroState.Working))
        assertEquals(OruHeroGlyph.Done, oruHeroGlyph(OruHeroState.Done))
        assertEquals(OruHeroGlyph.Failed, oruHeroGlyph(OruHeroState.Failed))

        assertFalse(oruHeroIsCircle(OruHeroState.Ready))
        assertTrue(oruHeroIsCircle(OruHeroState.Working))
        assertTrue(oruHeroIsCircle(OruHeroState.Done))
        assertTrue(oruHeroIsCircle(OruHeroState.Failed))
    }

    @Test
    fun `hero tile size is largest ready, larger when running, and compact everywhere when compact`() {
        assertEquals(132, oruHeroTileSizeDp(OruHeroState.Ready, compact = false))
        assertEquals(152, oruHeroTileSizeDp(OruHeroState.Working, compact = false))
        assertEquals(152, oruHeroTileSizeDp(OruHeroState.Failed, compact = false))
        assertEquals(84, oruHeroTileSizeDp(OruHeroState.Working, compact = true))
        assertEquals(84, oruHeroTileSizeDp(OruHeroState.Ready, compact = true))
    }

    @Test
    fun `bar fraction is clamped and a missing scale draws nothing`() {
        assertEquals(0.5f, oruBarFraction(512, 1024), 0.0001f)
        assertEquals(1f, oruBarFraction(2000, 1024), 0.0001f)
        assertEquals(0f, oruBarFraction(-4, 1024), 0.0001f)
        assertEquals(0f, oruBarFraction(512, 0), 0.0001f)
        assertTrue(oruBarIsHot(0.9f))
        assertFalse(oruBarIsHot(0.5f))
    }

    @Test
    fun `graph scale prefers the known limit, then the largest sample, never below one`() {
        assertEquals(1024, oruGraphScale(limit = 1024, samples = listOf(10, 900)))
        assertEquals(900, oruGraphScale(limit = null, samples = listOf(10, 900)))
        assertEquals(1, oruGraphScale(limit = null, samples = emptyList()))
        assertEquals(1, oruGraphScale(limit = 0, samples = listOf(0)))
    }

    @Test
    fun `missing figure reads as a dash and never as zero`() {
        assertEquals("-", oruFigure(null))
        assertEquals("0", oruFigure(0))
        assertEquals("42", oruFigure(42))
    }

    @Test
    fun `frosted fill is translucent and solid fill is opaque`() {
        assertEquals(0.42f, oruFillAlpha(OruSurfaceFill.Frosted, 0.42f), 0.0001f)
        assertEquals(1f, oruFillAlpha(OruSurfaceFill.Solid, 0.42f), 0.0001f)
    }

    @Test
    fun `tile letter is the first letter in upper case`() {
        assertEquals("V", oruTileLetter("video player"))
        assertEquals("M", oruTileLetter("  music"))
        assertEquals("?", oruTileLetter(""))
    }
}
