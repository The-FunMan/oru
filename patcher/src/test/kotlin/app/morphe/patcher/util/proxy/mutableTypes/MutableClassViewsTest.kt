/*
 * Copyright 2026 Morphe.
 * https://github.com/morpheapp/morphe-patcher
 */

package app.morphe.patcher.util.proxy.mutableTypes

import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal object MutableClassViewsTest {
    private const val CLASS_TYPE = "Lcom/test/TestClass;"

    private fun method(name: String, accessFlags: Int) = ImmutableMethod(
        CLASS_TYPE,
        name,
        emptyList(),
        "V",
        accessFlags,
        null, null, null
    ).toMutable()

    private fun classWith(vararg methods: MutableMethod) = MutableClass(
        ImmutableClassDef(
            CLASS_TYPE,
            0, null, null, null, null, null,
            methods.toList(),
        )
    )

    private val directMethod = method("direct", AccessFlags.PRIVATE.value)
    private val virtualMethod = method("virtual", AccessFlags.PUBLIC.value)

    @Test
    fun `method added to methods after directMethods was read is in directMethods`() {
        val mutableClass = classWith(virtualMethod)
        assertEquals(0, mutableClass.directMethods.size)

        val added = method("added", AccessFlags.STATIC.value or AccessFlags.PRIVATE.value)
        mutableClass.methods.add(added)

        assertTrue(added in mutableClass.directMethods)
        assertEquals(1, mutableClass.directMethods.size)
        assertEquals(1, mutableClass.virtualMethods.size)
    }

    @Test
    fun `method removed from methods after virtualMethods was read is gone from virtualMethods`() {
        val mutableClass = classWith(virtualMethod, directMethod)
        assertEquals(1, mutableClass.virtualMethods.size)

        mutableClass.methods.remove(virtualMethod)

        assertFalse(virtualMethod in mutableClass.virtualMethods)
        assertEquals(0, mutableClass.virtualMethods.size)
        assertEquals(listOf(directMethod), mutableClass.directMethods.toList())
    }

    @Test
    fun `adding through directMethods adds to methods`() {
        val mutableClass = classWith(virtualMethod)
        val added = method("added", AccessFlags.PRIVATE.value)

        mutableClass.directMethods.add(added)

        assertTrue(added in mutableClass.methods)
        assertEquals(2, mutableClass.methods.size)
    }

    @Test
    fun `adding a virtual method through directMethods is rejected`() {
        val mutableClass = classWith(directMethod)

        assertFailsWith<IllegalArgumentException> {
            mutableClass.directMethods.add(virtualMethod)
        }
        assertEquals(1, mutableClass.methods.size)
    }

    @Test
    fun `views keep the order of methods`() {
        val first = method("first", AccessFlags.PRIVATE.value)
        val second = method("second", AccessFlags.PUBLIC.value)
        val third = method("third", AccessFlags.PRIVATE.value)
        val mutableClass = classWith(first, second, third)

        assertEquals(listOf(first, third), mutableClass.directMethods.toList())
        assertEquals(listOf(second), mutableClass.virtualMethods.toList())
        // ImmutableClassDef lists direct methods before virtual methods.
        assertEquals(listOf(first, third, second), mutableClass.methods.toList())
    }
}
