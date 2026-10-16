/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.screen.shared

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class FilePickerSortTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val base = 1_700_000_000_000L

    private fun file(name: String, modifiedAt: Long): File =
        folder.newFile(name).also { it.setLastModified(modifiedAt) }

    @Test
    fun `a picker opens newest first when nothing usable is stored`() {
        assertEquals(SortMode.DATE_DESC, initialSortMode(null))
        assertEquals(SortMode.DATE_DESC, initialSortMode(""))
        assertEquals(SortMode.DATE_DESC, initialSortMode("NOT_A_SORT_MODE"))
    }

    @Test
    fun `a stored sort choice is kept`() {
        assertEquals(SortMode.NAME_ASC, initialSortMode("NAME_ASC"))
        assertEquals(SortMode.SIZE_DESC, initialSortMode("SIZE_DESC"))
    }

    @Test
    fun `newest first puts the most recently changed files at the top with folders still first`() {
        val middle = file("middle.apk", base + 1_000)
        val newest = file("newest.apk", base + 2_000)
        val older = file("older.apk", base)
        val zeta = folder.newFolder("zeta")
        val alpha = folder.newFolder("alpha")

        val sorted = applySort(listOf(older, zeta, newest, alpha, middle), SortMode.DATE_DESC)

        assertEquals(
            listOf(alpha, zeta, newest, middle, older).map { it.name },
            sorted.map { it.name },
        )
    }
}
