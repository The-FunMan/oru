/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.util

import kotlin.test.Test
import kotlin.test.assertEquals

class OruSourceNamesTest {

    @Test
    fun `oru shows the default source under the neutral label`() {
        assertEquals(
            "Default patches",
            OruSourceNames.displayName("Morphe Patches", isDefault = true, oruUx = true)
        )
    }

    @Test
    fun `oru keeps the names of other sources`() {
        assertEquals(
            "My source",
            OruSourceNames.displayName("My source", isDefault = false, oruUx = true)
        )
    }

    @Test
    fun `morphe shows every name as stored`() {
        assertEquals(
            "Morphe Patches",
            OruSourceNames.displayName("Morphe Patches", isDefault = true, oruUx = false)
        )
        assertEquals(
            "My source",
            OruSourceNames.displayName("My source", isDefault = false, oruUx = false)
        )
    }

    @Test
    fun `the label does not depend on the stored name of the default source`() {
        assertEquals(
            OruSourceNames.DEFAULT_LABEL,
            OruSourceNames.displayName("Some other stored name", isDefault = true, oruUx = true)
        )
    }
}
