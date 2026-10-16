/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OruHomeBannerTest {

    private val idle = OruBatchSignal(OruBatchPhase.Idle)
    private val running = OruBatchSignal(OruBatchPhase.Running, processed = 2, total = 5)
    private val finished = OruBatchSignal(OruBatchPhase.Finished)

    private fun input(
        manager: OruManagerUpdate? = null,
        sources: Boolean = false,
        metered: Boolean = false,
        batch: OruBatchSignal = idle,
        bundle: OruBundleUpdateState? = null,
    ) = OruBannerInput(
        managerUpdate = manager,
        sourcesAttention = sources,
        meteredSkipped = metered,
        batch = batch,
        bundleUpdate = bundle,
    )

    @Test
    fun `nothing to report means no banner`() {
        assertNull(pickOruHomeBanner(input(), repatchCount = 0))
    }

    @Test
    fun `a manager update outranks every other source`() {
        val update = OruManagerUpdate.Available("v1.0.0")
        val banner = pickOruHomeBanner(
            input(manager = update, sources = true, metered = true, batch = finished, bundle = OruBundleUpdateState.Updating),
            repatchCount = 3,
        )
        assertEquals(OruHomeBanner.ManagerUpdate(update), banner)
    }

    @Test
    fun `every state of a manager update shows on the banner`() {
        val states = listOf(
            OruManagerUpdate.Downloading(percent = 40),
            OruManagerUpdate.Downloading(percent = null),
            OruManagerUpdate.Ready,
            OruManagerUpdate.Installing,
            OruManagerUpdate.Failed("The download does not match its checksum."),
        )
        for (state in states) {
            assertEquals(OruHomeBanner.ManagerUpdate(state), pickOruHomeBanner(input(manager = state), repatchCount = 0))
        }
    }

    @Test
    fun `source problems outrank metered skips, batches, bundle updates and repatches`() {
        val banner = pickOruHomeBanner(
            input(sources = true, metered = true, batch = running, bundle = OruBundleUpdateState.Updating),
            repatchCount = 3,
        )
        assertEquals(OruHomeBanner.SourcesAttention, banner)
    }

    @Test
    fun `metered skips outrank the batch and the bundle`() {
        val banner = pickOruHomeBanner(input(metered = true, batch = running, bundle = OruBundleUpdateState.Updating), repatchCount = 1)
        assertEquals(OruHomeBanner.Metered, banner)
    }

    @Test
    fun `a running batch outranks a finished one, the bundle and the repatch count`() {
        val banner = pickOruHomeBanner(input(batch = running, bundle = OruBundleUpdateState.Updating), repatchCount = 1)
        assertEquals(OruHomeBanner.BatchRunning(processed = 2, total = 5), banner)
    }

    @Test
    fun `a finished batch outranks the bundle update and the repatch count`() {
        val banner = pickOruHomeBanner(input(batch = finished, bundle = OruBundleUpdateState.Updating), repatchCount = 4)
        assertEquals(OruHomeBanner.BatchFinished, banner)
    }

    @Test
    fun `a bundle update outranks the repatch count`() {
        val banner = pickOruHomeBanner(input(bundle = OruBundleUpdateState.Updating), repatchCount = 4)
        assertEquals(OruHomeBanner.BundleUpdate(OruBundleUpdateState.Updating), banner)
    }

    @Test
    fun `a finished or failed bundle update shows its own state`() {
        assertEquals(
            OruHomeBanner.BundleUpdate(OruBundleUpdateState.Done),
            pickOruHomeBanner(input(bundle = OruBundleUpdateState.Done), repatchCount = 0),
        )
        assertEquals(
            OruHomeBanner.BundleUpdate(OruBundleUpdateState.Failed),
            pickOruHomeBanner(input(bundle = OruBundleUpdateState.Failed), repatchCount = 0),
        )
    }

    @Test
    fun `the repatch count is the last to show`() {
        assertEquals(OruHomeBanner.Repatch(4), pickOruHomeBanner(input(), repatchCount = 4))
    }
}
