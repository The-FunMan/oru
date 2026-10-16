/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.installer

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The hidden system classes are stood in for by fakes that have, or lack, the members the installer uses. */
class ShizukuHiddenApiTest {
    private class FakeOwner(val name: String) {
        constructor() : this("default")
    }

    private class FakeParams {
        @Suppress("unused")
        var installFlags: Int = 4
    }

    private class FakeParamsWithoutFlags

    @Test
    fun `a present hidden constructor is called with its arguments`() {
        val owner = newHiddenInstance(FakeOwner::class.java, arrayOf(String::class.java), "shell")

        assertEquals("shell", owner.name)
    }

    @Test
    fun `a missing hidden constructor is reported with the class and parameter types`() {
        val error = assertFailsWith<HiddenApiException> {
            newHiddenInstance(FakeOwner::class.java, arrayOf(String::class.java, Int::class.javaPrimitiveType!!), "a", 1)
        }

        assertContains(error.message!!, FakeOwner::class.java.name)
        assertContains(error.message!!, "int")
    }

    @Test
    fun `the install flag is added to the existing flags`() {
        val params = FakeParams()

        setHiddenInstallFlag(params, 0x2)

        assertEquals(6, params.installFlags)
    }

    @Test
    fun `a missing installFlags field is reported instead of ignored`() {
        val error = assertFailsWith<HiddenApiException> {
            setHiddenInstallFlag(FakeParamsWithoutFlags(), 0x2)
        }

        assertContains(error.message!!, "installFlags")
        assertContains(error.message!!, FakeParamsWithoutFlags::class.java.name)
    }
}
