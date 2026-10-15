/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.installer

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The poll and the timeout verdict run on a fake clock, so the limits are exact and the test takes no time. */
class InstallWaitPolicyTest {

    /** A clock that only moves when [sleep] is called, and a record of every sleep. */
    private class FakeClock {
        var now = 0L
        val sleeps = mutableListOf<Long>()

        fun sleep(ms: Long) {
            sleeps += ms
            now += ms
        }
    }

    @Test
    fun `poll returns at once when the probe already succeeds`() = runBlocking {
        val clock = FakeClock()
        var probes = 0

        val done = InstallWaitPolicy.pollUntil(
            limitMs = 20_000L,
            intervalMs = 500L,
            now = { clock.now },
            sleep = { clock.sleep(it) },
            probe = { probes++; true },
        )

        assertTrue(done)
        assertEquals(1, probes)
        assertEquals(emptyList(), clock.sleeps)
    }

    @Test
    fun `poll reports success when the package goes away part way through`() = runBlocking {
        val clock = FakeClock()

        val done = InstallWaitPolicy.pollUntil(
            limitMs = 20_000L,
            intervalMs = 500L,
            now = { clock.now },
            sleep = { clock.sleep(it) },
            probe = { clock.now >= 3_000L },
        )

        assertTrue(done)
        assertEquals(3_000L, clock.now)
        assertEquals(List(6) { 500L }, clock.sleeps)
    }

    @Test
    fun `poll gives up at the limit and never sleeps past it`() = runBlocking {
        val clock = FakeClock()
        var probes = 0

        val done = InstallWaitPolicy.pollUntil(
            limitMs = 1_200L,
            intervalMs = 500L,
            now = { clock.now },
            sleep = { clock.sleep(it) },
            probe = { probes++; false },
        )

        assertFalse(done)
        assertEquals(1_200L, clock.now)
        // The last sleep is cut to what is left of the limit, and the probe runs once more at the limit
        assertEquals(listOf(500L, 500L, 200L), clock.sleeps)
        assertEquals(4, probes)
    }

    @Test
    fun `poll with a zero limit probes once and does not sleep`() = runBlocking {
        val clock = FakeClock()
        var probes = 0

        val done = InstallWaitPolicy.pollUntil(
            limitMs = 0L,
            intervalMs = 500L,
            now = { clock.now },
            sleep = { clock.sleep(it) },
            probe = { probes++; false },
        )

        assertFalse(done)
        assertEquals(1, probes)
        assertEquals(emptyList(), clock.sleeps)
    }

    @Test
    fun `an install whose version code matches the archive has landed`() {
        assertTrue(InstallWaitPolicy.installLanded(installedVersionCode = 4_200L, archiveVersionCode = 4_200L))
    }

    @Test
    fun `an install with another version code, a missing package or an unreadable archive has not landed`() {
        assertFalse(InstallWaitPolicy.installLanded(installedVersionCode = 4_199L, archiveVersionCode = 4_200L))
        assertFalse(InstallWaitPolicy.installLanded(installedVersionCode = null, archiveVersionCode = 4_200L))
        assertFalse(InstallWaitPolicy.installLanded(installedVersionCode = 4_200L, archiveVersionCode = null))
    }
}
