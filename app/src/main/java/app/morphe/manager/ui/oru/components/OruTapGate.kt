/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.awaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Takes one tap and ignores the taps that follow it within [windowMs], so a double tap on a navigation control or on
 * a button that sits where another one was does not act twice. A rejected tap does not start a window of its own.
 *
 * A screen that has just appeared is armed with [arm]: the taps it gets in its first moments (the second tap of a
 * double tap that landed on it) are ignored too.
 *
 * @param clock the time in milliseconds. The default is monotonic, so a change of the wall clock does not reopen the gate.
 */
class OruTapGate(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) {
    private var lastAt: Long? = null

    /** Starts a quiet window now, for a screen that has just appeared. */
    fun arm() {
        lastAt = clock()
    }

    /** Whether the gate is open now, without taking a tap. */
    fun isOpen(): Boolean {
        val last = lastAt ?: return true
        return clock() - last >= windowMs
    }

    /** Whether a tap is taken now. A taken tap starts the next window. */
    fun accept(): Boolean {
        if (!isOpen()) return false
        lastAt = clock()
        return true
    }

    companion object {
        /** The window a double tap is folded into. Short enough that a deliberate second tap still works. */
        const val DEFAULT_WINDOW_MS = 400L

        private const val NANOS_PER_MILLI = 1_000_000L
    }
}

/**
 * Takes the pointer input away while [gate] is closed: the taps that a double tap sends into a screen as it opens do not
 * reach its controls, or the scrim that dismisses it. It consumes in the initial pass, before the controls see the events.
 */
fun Modifier.oruTapSettle(gate: OruTapGate): Modifier = pointerInput(gate) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (!gate.isOpen()) event.changes.forEach { it.consume() }
        }
    }
}
