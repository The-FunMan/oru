/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.util

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AvatarFallbackTest {
    @Test
    fun healthyPrimarySkipsFallback() = runBlocking {
        val requested = mutableListOf<String>()

        val result = loadWithFallback("primary", "fallback") { url ->
            requested += url
            "loaded:$url"
        }

        assertEquals("loaded:primary", result)
        assertEquals(listOf("primary"), requested)
    }

    @Test
    fun failedPrimaryFallsBack() = runBlocking {
        val requested = mutableListOf<String>()

        val result = loadWithFallback("primary", "fallback") { url ->
            requested += url
            if (url == "fallback") "loaded:$url" else null
        }

        assertEquals("loaded:fallback", result)
        assertEquals(listOf("primary", "fallback"), requested)
    }

    @Test
    fun missingPrimaryUsesFallbackOnly() = runBlocking {
        val requested = mutableListOf<String>()

        val result = loadWithFallback(null, "fallback") { url ->
            requested += url
            "loaded:$url"
        }

        assertEquals("loaded:fallback", result)
        assertEquals(listOf("fallback"), requested)
    }

    @Test
    fun nothingToLoadReturnsNull() = runBlocking {
        var calls = 0

        val result = loadWithFallback<String>(null, null) {
            calls++
            "unreachable"
        }

        assertNull(result)
        assertEquals(0, calls)
    }
}
