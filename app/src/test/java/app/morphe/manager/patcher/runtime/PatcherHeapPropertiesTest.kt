/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.patcher.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class PatcherHeapPropertiesTest {
    @Test
    fun `the heap limits follow the requested size`() {
        val props = patcherHeapProperties("1024M")

        assertEquals("1024M", props["PROP_dalvik.vm.heapsize"])
        assertEquals("1024M", props["PROP_dalvik.vm.heapgrowthlimit"])
    }

    @Test
    fun `the free space bounds do not depend on the device defaults`() {
        val props = patcherHeapProperties("640M")

        assertEquals("8m", props["PROP_dalvik.vm.heapminfree"])
        assertEquals("32m", props["PROP_dalvik.vm.heapmaxfree"])
    }
}
