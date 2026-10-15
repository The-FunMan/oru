/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.run

import java.util.Locale

/**
 * Durations and sizes of a patch run, for the stage rail and the done facts. The numbers use a dot
 * whatever the phone language, and the units are the technical ones the readout already uses.
 */
object OruRunFormat {
    private const val MILLIS_PER_SECOND = 1_000L
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val BYTES_PER_KB = 1024.0
    private const val BYTES_PER_MB = 1024.0 * 1024.0
    private const val MAX_PERCENT = 100

    /** A stage's duration: "0.9 s" under a minute, then the minutes and seconds of [elapsed]. */
    fun stageDuration(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        return if (clamped < MILLIS_PER_MINUTE) {
            String.format(Locale.ROOT, "%.1f s", clamped.toDouble() / MILLIS_PER_SECOND)
        } else {
            elapsed(clamped)
        }
    }

    /** Minutes and seconds, for example "0:12" or "12:05". */
    fun elapsed(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0L) / MILLIS_PER_SECOND
        return String.format(
            Locale.ROOT,
            "%d:%02d",
            totalSeconds / SECONDS_PER_MINUTE,
            totalSeconds % SECONDS_PER_MINUTE
        )
    }

    /** An output size: "512 KB" under a megabyte, then "115.6 MB". */
    fun size(bytes: Long): String {
        val value = bytes.coerceAtLeast(0L).toDouble()
        return if (value < BYTES_PER_MB) {
            String.format(Locale.ROOT, "%.0f KB", value / BYTES_PER_KB)
        } else {
            String.format(Locale.ROOT, "%.1f MB", value / BYTES_PER_MB)
        }
    }

    /** A heap figure in megabytes, for example "336 MB". */
    fun megabytes(mb: Int): String = "${mb.coerceAtLeast(0)} MB"

    /** A progress percentage, for example "50%". The value is kept between 0 and 100. */
    fun percent(value: Int): String = "${value.coerceIn(0, MAX_PERCENT)}%"
}
