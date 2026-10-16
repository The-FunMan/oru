/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.theme

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import kotlin.math.PI
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OruMotionTest {
    @Test
    fun `every token snaps when motion is reduced`() {
        assertIs<SnapSpec<Float>>(OruMotion.tileBounds<Float>(reduced = true))
        assertIs<SnapSpec<Float>>(OruMotion.tileCorners<Float>(reduced = true))
        assertIs<SnapSpec<Float>>(OruMotion.press<Float>(reduced = true))
        assertIs<SnapSpec<Float>>(OruMotion.expand<Float>(reduced = true))
        assertIs<SnapSpec<Float>>(OruMotion.sheetRise<Float>(reduced = true))
        assertIs<SnapSpec<Float>>(OruMotion.swapText<Float>(reduced = true))
        assertIs<SnapSpec<Float>>(OruMotion.ringColor<Float>(reduced = true))
        assertIs<SnapSpec<Float>>(OruMotion.glassMorph<Float>(reduced = true))
        assertIs<SnapSpec<Float>>(OruMotion.selectPulse<Float>(reduced = true))
    }

    @Test
    fun `tokens animate when motion is not reduced`() {
        assertIs<SpringSpec<Float>>(OruMotion.tileBounds<Float>(reduced = false))
        assertIs<SpringSpec<Float>>(OruMotion.press<Float>(reduced = false))
        assertIs<TweenSpec<Float>>(OruMotion.swapText<Float>(reduced = false))
        assertIs<TweenSpec<Float>>(OruMotion.ringColor<Float>(reduced = false))
        assertIs<TweenSpec<Float>>(OruMotion.glassMorph<Float>(reduced = false))
        assertIs<TweenSpec<Float>>(OruMotion.selectPulse<Float>(reduced = false))
    }

    @Test
    fun `the glass morph and the select pulse finish within the longest one-shot duration`() {
        assertTrue(OruMotion.GlassMorphMillis in 300..OruMotion.MaxDurationMillis)
        assertTrue(OruMotion.SelectPulseMillis <= OruMotion.MaxDurationMillis)
        assertTrue(OruMotion.SelectPulsePeakScale > 1f && OruMotion.SelectPulsePeakScale < 1.15f)
    }

    @Test
    fun `animator scale zero counts as reduced`() {
        val state = OruSystemState(powerSaveMode = false, lowRamDevice = false, animatorDurationScale = 0f)

        assertTrue(OruMotion.reduced(state))
    }

    @Test
    fun `battery saver counts as reduced`() {
        val state = OruSystemState(powerSaveMode = true, lowRamDevice = false, animatorDurationScale = 1f)

        assertTrue(OruMotion.reduced(state))
    }

    @Test
    fun `normal and scaled-down animations are not reduced`() {
        assertFalse(OruMotion.reduced(OruSystemState(false, false, 1f)))
        assertFalse(OruMotion.reduced(OruSystemState(false, false, 0.5f)))
        // Low RAM alone does not turn motion off; it only turns the frost off
        assertFalse(OruMotion.reduced(OruSystemState(false, true, 1f)))
    }

    @Test
    fun `a spring's stiffness follows from its natural cycle`() {
        // Undamped period is 2 pi / sqrt(k), so a 0.5 s response is k = (2 pi / 0.5)^2
        assertEquals((2 * PI / 0.5).pow(2).toFloat(), oruSpringStiffness(0.5f), absoluteTolerance = 0.01f)
        assertEquals((2 * PI / 0.34).pow(2).toFloat(), oruSpringStiffness(0.34f), absoluteTolerance = 0.01f)
    }

    @Test
    fun `longer responses are softer springs`() {
        val snappy = oruSpringStiffness(OruMotion.SnappyResponseSeconds)
        val settle = oruSpringStiffness(OruMotion.SettleResponseSeconds)
        val soft = oruSpringStiffness(OruMotion.SoftResponseSeconds)
        val glide = oruSpringStiffness(OruMotion.GlideResponseSeconds)
        val tile = oruSpringStiffness(OruMotion.TileResponseSeconds)
        assertTrue(snappy > settle && settle > soft && soft > glide && glide > tile)
    }

    @Test
    fun `every spring settles within its budget, so no token drags past its motion`() {
        // The budgets are the settle times the tokens were tuned to: the 2 percent time, rounded up
        assertTrue(settle(OruMotion.SnappyResponseSeconds, OruMotion.SnappyDampingRatio) <= 0.22f)
        assertTrue(settle(OruMotion.SoftResponseSeconds, OruMotion.SoftDampingRatio) <= 0.36f)
        assertTrue(settle(OruMotion.SettleResponseSeconds, OruMotion.SettleDampingRatio) <= 0.27f)
        assertTrue(settle(OruMotion.GlideResponseSeconds, OruMotion.GlideDampingRatio) <= 0.34f)
        assertTrue(settle(OruMotion.TileResponseSeconds, OruMotion.TileFlightDampingRatio) <= 0.45f)
        assertTrue(settle(OruMotion.TileResponseSeconds, OruMotion.TileBoundsDampingRatio) <= 0.40f)
    }

    @Test
    fun `the settle time is the 2 percent envelope over the decay rate`() {
        // Decay rate is damping times 2 pi over the response: 0.8 * 2 pi / 0.28, so 3.9 over that is about 0.217 s
        assertEquals(3.9f / (0.8f * 2f * PI.toFloat() / 0.28f), oruSpringSettleSeconds(0.28f, 0.8f), absoluteTolerance = 1e-5f)
        assertEquals(0.217f, oruSpringSettleSeconds(0.28f, 0.8f), absoluteTolerance = 0.001f)
    }

    @Test
    fun `a stiffer response settles sooner at the same damping`() {
        assertTrue(settle(0.3f, 0.8f) < settle(0.6f, 0.8f))
    }

    private fun settle(response: Float, damping: Float): Float = oruSpringSettleSeconds(response, damping)

    @Test
    fun `the spring tokens are springs, and the flight of a tile is one too`() {
        assertIs<SpringSpec<Float>>(OruMotion.tileFlight<Float>(reduced = false))
        assertIs<SpringSpec<Float>>(OruMotion.springGlide<Float>(reduced = false))
        assertIs<SpringSpec<Float>>(OruMotion.springSettle<Float>(reduced = false))
        assertIs<SnapSpec<Float>>(OruMotion.tileFlight<Float>(reduced = true))
    }

    @Test
    fun `transitions stay in the owner's pace`() {
        // UI element transitions run 250 to 400 ms, and no one-shot runs past the cap
        assertTrue(OruMotion.SwapMillis in 250..400)
        assertTrue(OruMotion.ScrimFadeMillis in 250..400)
        assertTrue(OruMotion.ExpandFadeMillis in 150..250)
        assertTrue(OruMotion.ScreenFadeMillis in 250..400)
        assertTrue(OruMotion.CrossFadeMillis >= 200)
        assertTrue(OruMotion.FadeMillis >= 200)
        assertTrue(OruMotion.AccentMorphMillis in 450..OruMotion.MaxDurationMillis)
        assertEquals(600, OruMotion.MaxDurationMillis)
        assertTrue(OruMotion.CascadeCapMillis <= OruMotion.MaxDurationMillis)
        assertTrue(OruMotion.GlyphStaggerCapMillis <= OruMotion.MaxDurationMillis)
    }

    @Test
    fun `the reduced fallback of a card or dialog fade snaps`() {
        assertIs<SnapSpec<Float>>(OruMotion.fade<Float>(reduced = true))
        assertIs<TweenSpec<Float>>(OruMotion.fade<Float>(reduced = false))
    }

    @Test
    fun `reduced motion follows the system or the app switch`() {
        assertFalse(oruReducedMotion(systemReduced = false, animationsEnabled = true))
        assertTrue(oruReducedMotion(systemReduced = true, animationsEnabled = true))
        assertTrue(oruReducedMotion(systemReduced = false, animationsEnabled = false))
        assertTrue(oruReducedMotion(systemReduced = true, animationsEnabled = false))
    }
}
