/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.util

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

internal class StageOverlapTest {
    @Test
    fun `returns both results`() {
        val (first, second) = runInParallel("t-a", { 1 }, "t-b", { "two" })

        assertEquals(1, first)
        assertEquals("two", second)
    }

    @Test
    fun `runs both blocks at the same time`() {
        // Each block waits for the other to start. Run one after the other, this would time out.
        val started = CountDownLatch(2)
        val bothStarted = { started.countDown(); assertTrue(started.await(30, TimeUnit.SECONDS)) }

        runInParallel("t-a", bothStarted, "t-b", bothStarted)

        assertEquals(0L, started.count)
    }

    @Test
    fun `runs the blocks on threads other than the caller`() {
        val caller = Thread.currentThread()
        val threads = runInParallel("t-a", { Thread.currentThread() }, "t-b", { Thread.currentThread() })

        assertNotEquals(caller, threads.first)
        assertNotEquals(caller, threads.second)
        assertEquals("t-a", threads.first.name)
        assertEquals("t-b", threads.second.name)
    }

    @Test
    fun `a failure surfaces only after the other block has finished`() {
        val secondStarted = CountDownLatch(1)
        val firstFailed = CountDownLatch(1)
        val secondFinished = AtomicBoolean(false)
        val error = IllegalStateException("first")

        // The first block fails only once the second is running, and the second finishes only after that failure.
        val thrown = assertFailsWith<IllegalStateException> {
            runInParallel<Unit, Unit>("t-a", {
                assertTrue(secondStarted.await(30, TimeUnit.SECONDS))
                firstFailed.countDown()
                throw error
            }, "t-b", {
                secondStarted.countDown()
                assertTrue(firstFailed.await(30, TimeUnit.SECONDS))
                secondFinished.set(true)
            })
        }

        assertSame(error, thrown)
        assertTrue(secondFinished.get())
    }

    @Test
    fun `when both blocks fail the first failure is thrown`() {
        val thrown = assertFailsWith<IllegalStateException> {
            runInParallel<Unit, Unit>(
                "t-a", { throw IllegalStateException("first") },
                "t-b", { throw IllegalArgumentException("second") },
            )
        }

        assertEquals("first", thrown.message)
    }

    @Test
    fun `a failure of the second block is thrown`() {
        val thrown = assertFailsWith<IllegalArgumentException> {
            runInParallel<Unit, Unit>("t-a", {}, "t-b", { throw IllegalArgumentException("second") })
        }

        assertEquals("second", thrown.message)
    }

    @Test
    fun `an interrupt of the caller is re-asserted after both blocks have finished`() {
        val release = CountDownLatch(1)
        val secondFinished = AtomicBoolean(false)
        val interruptedAfterCall = AtomicBoolean(false)
        val caller = thread {
            runInParallel("t-a", { assertTrue(release.await(30, TimeUnit.SECONDS)) }, "t-b", { secondFinished.set(true) })
            interruptedAfterCall.set(Thread.currentThread().isInterrupted)
        }

        caller.interrupt()
        release.countDown()
        caller.join(30_000)

        assertTrue(secondFinished.get())
        assertTrue(interruptedAfterCall.get(), "the caller's interrupt was lost")
    }
}
