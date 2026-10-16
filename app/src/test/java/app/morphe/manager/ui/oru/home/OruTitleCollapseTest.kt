/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.home

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OruTitleCollapseTest {
    /** Float scales of the wordmark are not exact, so the ratio tests compare within a small tolerance. */
    private fun assertNear(expected: Float, actual: Float) {
        assertTrue(abs(expected - actual) <= 1e-5f, "expected $expected but was $actual")
    }

    @Test
    fun `the wordmark travels from its header scale to its bar scale, a ratio of the two sizes`() {
        // The header draws the wordmark at 1.8 times its bar size (about 43 sp against 24 sp)
        assertNear(1f / 1.8f, oruWordmarkTravelScale(headerScale = 1.8f, barScale = 1f))
        assertEquals(0.5f, oruWordmarkTravelScale(headerScale = 2f, barScale = 1f))
        assertEquals(1f, oruWordmarkTravelScale(headerScale = 2f, barScale = 2f))
    }

    @Test
    fun `the wordmark at the middle of the travel is half way between the header size and the bar size`() {
        val toScale = oruWordmarkTravelScale(headerScale = 1.8f, barScale = 1f)
        val middle = oruTitleTransform(progress = 0.5f, fromX = 0f, fromY = 0f, toX = 0f, toY = 0f, toScale = toScale)
        assertNear(1f - (1f - toScale) / 2f, middle.scale)
    }

    @Test
    fun `the smoothstep is 0 at 0, 1 at 1, half way at half, and clamps outside 0 to 1`() {
        assertEquals(0f, oruSmoothstep(0f))
        assertEquals(0.5f, oruSmoothstep(0.5f))
        assertEquals(1f, oruSmoothstep(1f))
        assertEquals(0f, oruSmoothstep(-3f))
        assertEquals(1f, oruSmoothstep(4f))
    }

    @Test
    fun `the smoothstep eases in and out, so it is below the line at a quarter and above it at three quarters`() {
        assertEquals(0.15625f, oruSmoothstep(0.25f))
        assertEquals(0.84375f, oruSmoothstep(0.75f))
    }

    @Test
    fun `the title is at rest at scroll 0 and in the bar once the collapse distance has scrolled`() {
        assertEquals(0f, oruTitleProgress(firstVisibleItem = 0, scrollOffsetPx = 0, collapsePx = 70f))
        assertEquals(1f, oruTitleProgress(firstVisibleItem = 0, scrollOffsetPx = 70, collapsePx = 70f))
        assertEquals(1f, oruTitleProgress(firstVisibleItem = 0, scrollOffsetPx = 300, collapsePx = 70f))
    }

    @Test
    fun `the title is in the bar once the first item is off screen, whatever the offset`() {
        assertEquals(1f, oruTitleProgress(firstVisibleItem = 1, scrollOffsetPx = 0, collapsePx = 70f))
    }

    @Test
    fun `the progress is the eased fraction of the scroll, so the midpoint is the midpoint`() {
        assertEquals(0.5f, oruTitleProgress(firstVisibleItem = 0, scrollOffsetPx = 35, collapsePx = 70f))
        assertEquals(0.15625f, oruTitleProgress(firstVisibleItem = 0, scrollOffsetPx = 20, collapsePx = 80f))
    }

    @Test
    fun `the progress rises with the scroll and never goes back`() {
        var last = -1f
        for (scroll in 0..90) {
            val progress = oruTitleProgress(firstVisibleItem = 0, scrollOffsetPx = scroll, collapsePx = 70f)
            assertTrue(progress >= last, "progress fell at scroll $scroll")
            assertTrue(progress in 0f..1f)
            last = progress
        }
    }

    @Test
    fun `without a collapse distance the title steps straight to the bar on any scroll`() {
        assertEquals(0f, oruTitleProgress(firstVisibleItem = 0, scrollOffsetPx = 0, collapsePx = 0f))
        assertEquals(1f, oruTitleProgress(firstVisibleItem = 0, scrollOffsetPx = 1, collapsePx = 0f))
    }

    @Test
    fun `the collapse distance is the rest top of the title minus the top of the bar title`() {
        assertEquals(70f, oruTitleCollapseDistance(restTopPx = 96f, barTitleTopPx = 26f, fallbackPx = 1f))
    }

    @Test
    fun `the collapse distance falls back when the bar title is not above the rest top, or not measured`() {
        assertEquals(42f, oruTitleCollapseDistance(restTopPx = 26f, barTitleTopPx = 26f, fallbackPx = 42f))
        assertEquals(42f, oruTitleCollapseDistance(restTopPx = 96f, barTitleTopPx = Float.NaN, fallbackPx = 42f))
        assertEquals(42f, oruTitleCollapseDistance(restTopPx = Float.NaN, barTitleTopPx = 26f, fallbackPx = 42f))
    }

    @Test
    fun `a phase is 0 before its start, 1 after its end, and linear between`() {
        assertEquals(0f, oruTitlePhase(progress = 0f, from = 0f, to = 0.5f))
        assertEquals(0.5f, oruTitlePhase(progress = 0.25f, from = 0f, to = 0.5f))
        assertEquals(1f, oruTitlePhase(progress = 0.5f, from = 0f, to = 0.5f))
        assertEquals(1f, oruTitlePhase(progress = 0.9f, from = 0f, to = 0.5f))
        assertEquals(0f, oruTitlePhase(progress = 0.4f, from = 0.5f, to = 1f))
        assertEquals(0.5f, oruTitlePhase(progress = 0.75f, from = 0.5f, to = 1f))
        assertEquals(1f, oruTitlePhase(progress = 1f, from = 0.5f, to = 1f))
    }

    @Test
    fun `the subtitle is gone by the middle of the collapse and the glass is full only at its end`() {
        // The subtitle alpha is 1 minus the first-half phase, the glass presence the second-half phase
        assertEquals(1f, 1f - oruTitlePhase(progress = 0f, from = 0f, to = 0.5f))
        assertEquals(0f, 1f - oruTitlePhase(progress = 0.5f, from = 0f, to = 0.5f))
        assertEquals(0f, oruTitlePhase(progress = 0.5f, from = 0.5f, to = 1f))
        assertEquals(1f, oruTitlePhase(progress = 1f, from = 0.5f, to = 1f))
    }

    @Test
    fun `the travelling wordmark rests where the header puts it at progress 0 and sits in the bar at progress 1`() {
        val rest = oruTitleTransform(
            progress = 0f,
            fromX = 16f, fromY = 96f, toX = 36f, toY = 26f, toScale = 0.5f,
        )
        assertEquals(16f, rest.x)
        assertEquals(96f, rest.y)
        assertEquals(1f, rest.scale)

        val bar = oruTitleTransform(
            progress = 1f,
            fromX = 16f, fromY = 96f, toX = 36f, toY = 26f, toScale = 0.5f,
        )
        assertEquals(36f, bar.x)
        assertEquals(26f, bar.y)
        assertEquals(0.5f, bar.scale)
    }

    @Test
    fun `the travelling wordmark is interpolated halfway between the rest and the bar`() {
        val middle = oruTitleTransform(
            progress = 0.5f,
            fromX = 16f, fromY = 96f, toX = 36f, toY = 26f, toScale = 0.5f,
        )
        assertEquals(26f, middle.x)
        assertEquals(61f, middle.y)
        assertEquals(0.75f, middle.scale)
    }

    @Test
    fun `the travelling wordmark clamps its progress to 0 and 1`() {
        val before = oruTitleTransform(progress = -1f, fromX = 0f, fromY = 0f, toX = 10f, toY = 10f, toScale = 0.5f)
        assertEquals(0f, before.x)
        assertEquals(1f, before.scale)

        val after = oruTitleTransform(progress = 3f, fromX = 0f, fromY = 0f, toX = 10f, toY = 10f, toScale = 0.5f)
        assertEquals(10f, after.x)
        assertEquals(0.5f, after.scale)
    }

    @Test
    fun `the anchor uses the fallback until the header and the bar are both measured`() {
        val anchor = OruTitleAnchor(fallbackPx = 70f)
        assertEquals(70f, anchor.collapsePx)
        assertFalse(anchor.ready)

        anchor.onHeaderMeasured(Offset(16f, 96f), firstVisibleItem = 0, scrollOffsetPx = 0)
        assertEquals(70f, anchor.collapsePx)
    }

    @Test
    fun `the anchor measures the collapse distance from the unscrolled title and the bar title`() {
        val anchor = OruTitleAnchor(fallbackPx = 70f)
        anchor.onBarMeasured(Offset(36f, 26f))
        // The title has scrolled 10 px: it is at 86 now, but its rest top is 96
        anchor.onHeaderMeasured(Offset(16f, 86f), firstVisibleItem = 0, scrollOffsetPx = 10)
        assertEquals(70f, anchor.collapsePx)
    }

    @Test
    fun `the anchor is ready once the title, the bar and the overlay are measured, and travels between them`() {
        val anchor = OruTitleAnchor(fallbackPx = 70f)
        anchor.onOverlayMeasured(Offset(0f, 24f))
        anchor.onBarMeasured(Offset(36f, 50f))
        anchor.onHeaderMeasured(Offset(16f, 120f), firstVisibleItem = 0, scrollOffsetPx = 0)
        assertTrue(anchor.ready)

        // Rest is the header's top in the overlay: (16, 96). The bar's title is (36, 26) in the overlay
        val rest = anchor.travel(progress = 0f, toScale = 0.5f)
        assertEquals(16f, rest.x)
        assertEquals(96f, rest.y)
        assertEquals(1f, rest.scale)

        val bar = anchor.travel(progress = 1f, toScale = 0.5f)
        assertEquals(36f, bar.x)
        assertEquals(26f, bar.y)
        assertEquals(0.5f, bar.scale)
    }

    @Test
    fun `the anchor's travel is the identity until it is ready`() {
        val anchor = OruTitleAnchor(fallbackPx = 70f)
        anchor.onHeaderMeasured(Offset(16f, 96f), firstVisibleItem = 0, scrollOffsetPx = 0)
        val step = anchor.travel(progress = 1f, toScale = 0.5f)
        assertEquals(0f, step.x)
        assertEquals(0f, step.y)
        assertEquals(1f, step.scale)
    }
}
