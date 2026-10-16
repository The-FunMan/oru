/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.util

import org.junit.jupiter.api.Assumptions.assumeFalse
import kotlin.test.Test

/**
 * Cost of the profiler hooks when `oru.profile` is off. Runs with `-Poru.bench=true`.
 *
 * Times the same loop with and without the hooks the patcher calls on hot paths. The hooks must not
 * allocate or take time measurable against the empty loop. Prints the median of each over [RUNS] runs.
 */
internal class PhaseProfilerOffBenchmark {
    @Test
    fun `profiler hooks cost nothing when profiling is off`() {
        assumeFalse(PhaseProfiler.enabled, "oru.profile is set")

        val baseline = mutableListOf<Long>()
        val hooked = mutableListOf<Long>()
        repeat(WARMUP + RUNS) { run ->
            val baseStart = System.nanoTime()
            var sink = baseLoop()
            val baseNanos = System.nanoTime() - baseStart

            val hookStart = System.nanoTime()
            sink += hookedLoop()
            val hookNanos = System.nanoTime() - hookStart

            check(sink != Long.MIN_VALUE)
            if (run >= WARMUP) {
                baseline += baseNanos
                hooked += hookNanos
            }
        }

        val baseMedian = baseline.sorted()[RUNS / 2]
        val hookMedian = hooked.sorted()[RUNS / 2]
        println(
            "PhaseProfilerOffBenchmark iterations=$ITERATIONS " +
                    "baseline median=${baseMedian / 1_000_000}ms (min ${baseline.min() / 1_000_000}, max ${baseline.max() / 1_000_000}) " +
                    "hooked median=${hookMedian / 1_000_000}ms (min ${hooked.min() / 1_000_000}, max ${hooked.max() / 1_000_000}) " +
                    "extra per iteration (four hook calls) ${"%.2f".format((hookMedian - baseMedian).toDouble() / ITERATIONS)} ns",
        )
    }

    private fun baseLoop(): Long {
        var sink = 0L
        for (i in 0 until ITERATIONS) {
            sink += i xor (i shr 3)
        }
        return sink
    }

    private fun hookedLoop(): Long {
        var sink = 0L
        for (i in 0 until ITERATIONS) {
            PhaseProfiler.count("bench.hook")
            val start = PhaseProfiler.nanoStart()
            PhaseProfiler.timeSince("bench.hook", start)
            PhaseProfiler.beginScan("match", "bench")?.end()
            sink += i xor (i shr 3)
        }
        return sink
    }

    private companion object {
        const val ITERATIONS = 20_000_000
        const val WARMUP = 2
        const val RUNS = 7
    }
}
