/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.patcher.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NativeLibsTest {
    private val arm64Device = listOf("arm64-v8a", "armeabi-v7a", "armeabi")

    @Test
    fun `keeps the 64-bit library on a device that runs both widths`() {
        assertEquals(
            "arm64-v8a",
            NativeLibs.preferredAbi(setOf("arm64-v8a", "armeabi-v7a"), arm64Device)
        )
    }

    @Test
    fun `falls back to the 32-bit library when the APK has no 64-bit one`() {
        assertEquals(
            "armeabi-v7a",
            NativeLibs.preferredAbi(setOf("armeabi-v7a", "x86"), arm64Device)
        )
    }

    @Test
    fun `keeps nothing when the APK has no library for the device`() {
        assertNull(NativeLibs.preferredAbi(setOf("x86", "x86_64"), arm64Device))
    }

    @Test
    fun `keeps nothing when the APK has no native libraries`() {
        assertNull(NativeLibs.preferredAbi(emptySet(), arm64Device))
    }

    @Test
    fun `keeps nothing when the device reports no ABIs`() {
        assertNull(NativeLibs.preferredAbi(setOf("arm64-v8a"), emptyList()))
    }
}
