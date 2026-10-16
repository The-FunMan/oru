package app.morphe.patcher

import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WriteStageProgressTest {
    private fun reportsOf(vararg phases: WritePhase): List<Float> {
        val reports = mutableListOf<Float>()
        val progress = WriteStageProgress(WriteProgressListener { reports += it })
        phases.forEach(progress::finished)
        return reports
    }

    private fun assertNear(expected: Double, actual: Float) {
        assertTrue(abs(expected.toFloat() - actual) < 1e-5f, "expected $expected but was $actual")
    }

    @Test
    fun `the phase shares add up to the whole write stage`() {
        assertEquals(54.8, WRITE_STAGE_SECONDS, 1e-9)
        assertEquals(WRITE_STAGE_SECONDS, WritePhase.entries.sumOf { it.measuredSeconds }, 1e-9)
    }

    @Test
    fun `each finished phase raises the fraction by its share`() {
        val reports = reportsOf(WritePhase.DEX_COMPILE, WritePhase.RESOURCE_COMPILE)

        assertEquals(2, reports.size)
        assertNear(12.9 / 54.8, reports[0])
        assertNear(43.1 / 54.8, reports[1])
    }

    @Test
    fun `phases finishing out of order still read as a rising fraction`() {
        // The dex and resource compile run side by side, so either may finish first
        val reports = reportsOf(WritePhase.RESOURCE_COMPILE, WritePhase.DEX_COMPILE)

        assertNear(30.2 / 54.8, reports[0])
        assertNear(43.1 / 54.8, reports[1])
        assertTrue(reports[1] > reports[0])
    }

    @Test
    fun `the reported fraction stops short of one, leaving the packing share`() {
        val afterCompile = reportsOf(WritePhase.DEX_COMPILE, WritePhase.RESOURCE_COMPILE).last()

        assertTrue(afterCompile in 0f..1f && afterCompile < 1f)
        assertNear(WritePhase.PACK.measuredSeconds / WRITE_STAGE_SECONDS, 1f - afterCompile)
    }

    @Test
    fun `pack progress scales across the pack share and ends at one`() {
        val reports = mutableListOf<Float>()
        val progress = WriteStageProgress(WriteProgressListener { reports += it })
        progress.finished(WritePhase.DEX_COMPILE)
        progress.finished(WritePhase.RESOURCE_COMPILE)

        progress.packProgress(0f)
        progress.packProgress(0.5f)
        progress.packProgress(1f)

        assertNear(43.1 / 54.8, reports[2])
        assertNear((43.1 + 11.7 * 0.5) / 54.8, reports[3])
        assertNear(1.0, reports[4])
    }

    @Test
    fun `without a listener finishing a phase does nothing`() {
        val progress = WriteStageProgress(null)

        progress.finished(WritePhase.DEX_COMPILE)
        progress.finished(WritePhase.RESOURCE_COMPILE)
        progress.packProgress(0.5f)
    }

    @Test
    fun `seeded pack progress is non-decreasing and does not re-emit earlier compile phases`() {
        val reports = mutableListOf<Float>()
        val stageProgress = WriteStageProgress(
            listener = WriteProgressListener { reports += it },
            initialSecondsDone = WritePhase.DEX_COMPILE.measuredSeconds + WritePhase.RESOURCE_COMPILE.measuredSeconds,
        )

        assertTrue(reports.isEmpty())

        stageProgress.packProgress(0.0f)
        stageProgress.packProgress(0.25f)
        stageProgress.packProgress(0.5f)
        stageProgress.packProgress(1.0f)

        assertEquals(4, reports.size)
        for (i in 0 until reports.size - 1) {
            assertTrue(reports[i] <= reports[i + 1], "Report at $i was not <= next")
        }
        assertNear(43.1 / 54.8, reports[0])
        assertNear(1.0, reports[3])
    }
}
