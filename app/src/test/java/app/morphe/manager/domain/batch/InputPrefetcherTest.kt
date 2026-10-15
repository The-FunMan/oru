/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.batch

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InputPrefetcherTest {
    private val prepared = AtomicInteger()
    private val discarded = mutableListOf<String>()

    private fun withPrefetcher(block: suspend (InputPrefetcher<String, String>) -> Unit) = runBlocking {
        coroutineScope {
            block(InputPrefetcher(this, discard = { discarded += it }))
        }
    }

    @Test
    fun preparedInputIsHandedOverWithoutPreparingItAgain() = withPrefetcher { inputs ->
        inputs.prefetch("b") { prepared.incrementAndGet(); "input-b" }

        val taken = inputs.take("b") { error("the prefetched input must be used") }

        assertEquals("input-b", taken)
        assertEquals(1, prepared.get())
        assertTrue(discarded.isEmpty())
    }

    @Test
    fun preparationRunsWhileTheCallerKeepsWorking() = withPrefetcher { inputs ->
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        inputs.prefetch("b") {
            started.complete(Unit)
            release.await()
            "input-b"
        }

        // prefetch returns before the preparation finishes, so the caller's own work overlaps it
        started.await()
        release.complete(Unit)

        assertEquals("input-b", inputs.take("b") { error("unused") })
    }

    @Test
    fun takingAnotherKeyDiscardsThePrefetchAndPreparesItsOwn() = withPrefetcher { inputs ->
        inputs.prefetch("b") { prepared.incrementAndGet(); "input-b" }

        yield() // the preparation has to finish before it can be discarded rather than cancelled
        val taken = inputs.take("c") { "input-c" }

        assertEquals("input-c", taken)
        assertEquals(listOf("input-b"), discarded)
    }

    @Test
    fun aPreparationThatYieldsNothingIsReturnedAsNull() = withPrefetcher { inputs ->
        inputs.prefetch("b") { null }

        assertNull(inputs.take("b") { error("the prefetch answered, it must not be asked again") })
        assertTrue(discarded.isEmpty())
    }

    @Test
    fun onlyOneInputIsPreparedAheadAtATime() = withPrefetcher { inputs ->
        inputs.prefetch("b") { "input-b" }

        assertFailsWith<IllegalStateException> { inputs.prefetch("c") { "input-c" } }
    }

    @Test
    fun closeDiscardsAFinishedPreparation() = withPrefetcher { inputs ->
        inputs.prefetch("b") { "input-b" }
        yield() // lets the preparation finish, so close has a produced input to discard

        inputs.close()

        assertEquals(listOf("input-b"), discarded)
    }

    @Test
    fun closeCancelsARunningPreparationWithoutDiscardingAnything() = withPrefetcher { inputs ->
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        inputs.prefetch("b") {
            started.complete(Unit)
            try {
                CompletableDeferred<Unit>().await()
                "input-b"
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
        }
        started.await()

        inputs.close()

        assertTrue(cancelled)
        assertTrue(discarded.isEmpty())
    }

    @Test
    fun closeWithNothingPreparedDoesNothing() = withPrefetcher { inputs ->
        inputs.close()

        assertTrue(discarded.isEmpty())
    }
}
