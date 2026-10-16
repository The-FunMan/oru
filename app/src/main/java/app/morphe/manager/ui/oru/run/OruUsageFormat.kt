/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.run

import java.util.Locale

/**
 * Strings for the usage readout. Numbers are padded with figure spaces (U+2007), which are as wide
 * as a digit in the system font, so a value changing every two seconds does not shift the text
 * next to it.
 */
object OruUsageFormat {
    private const val FIGURE_SPACE = ' '

    private const val HEAP_WIDTH = 4
    private const val CPU_WIDTH = 3
    private const val RATE_KB_WIDTH = 4

    /** Heap in megabytes, for example "  180 MB" with padding. */
    fun heapMb(mb: Int): String = "${tabular(mb.coerceAtLeast(0), HEAP_WIDTH)} MB"

    /** One core's load, clamped to 0 to 100, for example " 42%". */
    fun cpuPercent(percent: Int): String =
        "${tabular(percent.coerceIn(0, 100), CPU_WIDTH)}%"

    /**
     * A throughput in kilobytes per second. Below one megabyte it is shown as KB/s, above it as
     * MB/s with one decimal, for example "  512 KB/s" or "  1.5 MB/s".
     */
    fun ioRate(kbPerSec: Int): String {
        val kb = kbPerSec.coerceAtLeast(0)
        return if (kb < 1024) {
            "${tabular(kb, RATE_KB_WIDTH)} KB/s"
        } else {
            val mb = String.format(Locale.ROOT, "%.1f", kb / 1024.0)
            "${mb.padStart(RATE_KB_WIDTH, FIGURE_SPACE)} MB/s"
        }
    }

    private fun tabular(value: Int, width: Int): String = value.toString().padStart(width, FIGURE_SPACE)
}
