/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.patcher.runtime

import app.morphe.manager.patcher.runtime.ProcessRuntime.HeapExhaustedException
import app.morphe.manager.patcher.runtime.ProcessRuntime.HeapLimitIgnoredException
import app.morphe.manager.patcher.runtime.ProcessRuntime.ProcessConnectTimeoutException
import app.morphe.manager.patcher.runtime.ProcessRuntime.ProcessExitException
import app.morphe.manager.patcher.runtime.ProcessRuntime.RemoteFailureException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ProcessRuntimeFailureTest {
    private val EXTENDED_LIMIT = 2048

    @Test
    fun `lowers the limit one slider step inside the safe range`() {
        assertEquals(1152, lowerMemoryLimit(1280))
        assertEquals(640, lowerMemoryLimit(768))
    }

    @Test
    fun `lowers the limit two steps at a time in the extended range`() {
        assertEquals(1792, lowerMemoryLimit(2048))
        assertEquals(1536, lowerMemoryLimit(1792))
        assertEquals(1280, lowerMemoryLimit(1536))
    }

    @Test
    fun `never lowers below the minimum`() {
        assertEquals(512, lowerMemoryLimit(640))
        assertEquals(512, lowerMemoryLimit(600))
        assertEquals(512, lowerMemoryLimit(512))
    }

    @Test
    fun `every limit either drops or sits at the floor`() {
        for (limit in PROCESS_RUNTIME_MEMORY_MINIMUM..EXTENDED_LIMIT) {
            val next = lowerMemoryLimit(limit)
            assertTrue(next >= PROCESS_RUNTIME_MEMORY_MINIMUM, "$limit lowered to $next")
            if (limit > PROCESS_RUNTIME_MEMORY_MINIMUM) {
                assertTrue(next < limit, "$limit did not drop, got $next")
            } else {
                assertEquals(limit, next)
            }
        }
    }

    @Test
    fun `a retry ladder from the extended maximum reaches the floor in a few steps`() {
        var limit = EXTENDED_LIMIT
        var steps = 0
        while (limit > PROCESS_RUNTIME_MEMORY_MINIMUM) {
            limit = lowerMemoryLimit(limit)
            steps++
            assertTrue(steps <= 16, "ladder did not reach the floor")
        }
        assertEquals(PROCESS_RUNTIME_MEMORY_MINIMUM, limit)
    }

    @Test
    fun `only limits above the safe maximum are extended`() {
        assertFalse(isExtendedMemoryLimit(PROCESS_RUNTIME_MEMORY_MAX_LIMIT))
        assertTrue(isExtendedMemoryLimit(PROCESS_RUNTIME_MEMORY_MAX_LIMIT + 1))
    }

    @Test
    fun `only reclaimable failures retry`() {
        val reclaimable = listOf(
            ProcessExitException(ProcessRuntime.OOM_EXIT_CODE, 1024),
            ProcessExitException(ProcessRuntime.SIGKILL_EXIT_CODE, 1024),
            ProcessExitException(ProcessRuntime.SIGSEGV_EXIT_CODE, 1024),
        )
        val notReclaimable = listOf(
            ProcessExitException(1, 1024),
            ProcessExitException(ProcessRuntime.SIGSYS_EXIT_CODE, 1024),
            RemoteFailureException("java.lang.IllegalStateException"),
            HeapExhaustedException(1024, "java.lang.OutOfMemoryError"),
            ProcessConnectTimeoutException(30.seconds),
            HeapLimitIgnoredException(1280, 512),
            IllegalStateException(),
        )
        with(ProcessRuntime) {
            reclaimable.forEach { assertTrue(it.isReclaimableMemoryFailure(), "${it.message} should retry") }
            notReclaimable.forEach { assertFalse(it.isReclaimableMemoryFailure(), "${it.message} should not retry") }
        }
    }

    @Test
    fun `every failure the app shows has a message`() {
        val failures = listOf(
            RemoteFailureException("java.lang.IllegalStateException"),
            ProcessExitException(1, 640),
            HeapExhaustedException(640, "java.lang.OutOfMemoryError"),
            ProcessConnectTimeoutException(30.seconds),
            HeapLimitIgnoredException(1280, 512),
        )
        failures.forEach { failure ->
            assertFalse(
                failure.message.isNullOrBlank(),
                "${failure::class.simpleName} has no message"
            )
        }
    }

    @Test
    fun `remote failure message carries the cause from the remote stack trace`() {
        val failure = RemoteFailureException("java.lang.IllegalStateException: boom\n\tat app.Patch.run(Patch.kt:1)")

        assertTrue(failure.message!!.endsWith("java.lang.IllegalStateException: boom"))
    }
}
