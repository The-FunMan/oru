/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.patcher.runtime.process

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class LoadAndPrepareConcurrentlyTest {
    private class Boom : RuntimeException("boom")

    @Test
    fun `returns both results and runs the two steps at the same time`() = runBlocking {
        val loadStarted = CompletableDeferred<Unit>()
        val prepareStarted = CompletableDeferred<Unit>()

        // A sequential run would wait forever on the other step and hit the timeout
        val (patches, prepared) = withTimeout(5_000) {
            loadAndPrepareConcurrently(
                load = {
                    loadStarted.complete(Unit)
                    prepareStarted.await()
                    "patches"
                },
                prepare = {
                    prepareStarted.complete(Unit)
                    loadStarted.await()
                    "prepared"
                },
                cleanup = { fail("a returned result is not cleaned up") }
            )
        }

        assertEquals("patches", patches)
        assertEquals("prepared", prepared)
    }

    @Test
    fun `a load failure cancels the preparation and surfaces the load error`() = runBlocking {
        val prepareStarted = CompletableDeferred<Unit>()
        val prepareCancelled = CompletableDeferred<Unit>()

        val thrown = assertFailsWith<Boom> {
            loadAndPrepareConcurrently(
                load = {
                    prepareStarted.await()
                    throw Boom()
                },
                prepare = {
                    prepareStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        prepareCancelled.complete(Unit)
                    }
                },
                cleanup = { fail("nothing was prepared") }
            )
        }

        assertEquals("boom", thrown.message)
        assertTrue(prepareCancelled.isCompleted)
    }

    @Test
    fun `a preparation failure cancels the load and surfaces the preparation error`() = runBlocking {
        val loadStarted = CompletableDeferred<Unit>()
        val loadCancelled = CompletableDeferred<Unit>()

        val thrown = assertFailsWith<Boom> {
            loadAndPrepareConcurrently(
                load = {
                    loadStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        loadCancelled.complete(Unit)
                    }
                },
                prepare = {
                    loadStarted.await()
                    throw Boom()
                },
                cleanup = { fail("nothing was prepared") }
            )
        }

        assertEquals("boom", thrown.message)
        assertTrue(loadCancelled.isCompleted)
    }

    @Test
    fun `a finished preparation is cleaned up when the load fails after it`() = runBlocking {
        val prepareDone = CompletableDeferred<Unit>()
        val cleaned = mutableListOf<String>()

        val thrown = assertFailsWith<Boom> {
            loadAndPrepareConcurrently(
                load = {
                    prepareDone.await()
                    throw Boom()
                },
                prepare = { "prepared".also { prepareDone.complete(Unit) } },
                cleanup = { cleaned += it }
            )
        }

        assertEquals("boom", thrown.message)
        assertEquals(listOf("prepared"), cleaned)
    }

    @Test
    fun `cancelling the caller cancels both steps`() = runBlocking {
        val prepareCancelled = CompletableDeferred<Unit>()

        val thrown = assertFailsWith<TimeoutCancellationException> {
            withTimeout(100) {
                loadAndPrepareConcurrently(
                    load = { awaitCancellation() },
                    prepare = {
                        try {
                            awaitCancellation()
                        } finally {
                            prepareCancelled.complete(Unit)
                        }
                    },
                    cleanup = { fail("nothing was prepared") }
                )
            }
        }

        assertIs<TimeoutCancellationException>(thrown)
        assertTrue(prepareCancelled.isCompleted)
    }
}
