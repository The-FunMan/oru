/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.bundles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SourceRemovalTest {

    private val default = AppSourceFact(
        uid = 0, name = "Morphe Patches", isDefault = true, repositoryPath = null,
        patchVersion = null, patchCount = 0, recommendedVersion = null,
    )

    private val mine = AppSourceFact(
        uid = 4, name = "Mine", isDefault = false, repositoryPath = "owner/repo",
        patchVersion = null, patchCount = 0, recommendedVersion = null,
    )

    @Test
    fun `an app patched from the removed source moves to the source it falls back to`() {
        // YouTube turns the default source down, so it is patched from the other one
        val moves = SourceRemoval.appsMovedOff(
            removedUid = 4,
            covering = mapOf("com.google.android.youtube" to listOf(default, mine)),
            keptFrom = mapOf("com.google.android.youtube" to setOf(0)),
        )
        assertEquals(listOf("com.google.android.youtube"), moves.map { it.packageName })
        assertEquals(0, moves.single().to.uid)
    }

    @Test
    fun `an app patched from another source does not move`() {
        val moves = SourceRemoval.appsMovedOff(
            removedUid = 4,
            covering = mapOf("com.twitter" to listOf(default, mine)),
            keptFrom = emptyMap(),
        )
        assertEquals(emptyList(), moves)
    }

    @Test
    fun `an app no other source covers is left out`() {
        val moves = SourceRemoval.appsMovedOff(
            removedUid = 4,
            covering = mapOf("com.reddit.frontpage" to listOf(mine)),
            keptFrom = emptyMap(),
        )
        assertEquals(emptyList(), moves)
    }

    @Test
    fun `the shared target is the source every app moves to`() {
        val moves = listOf(AppMovedOff("a", default), AppMovedOff("b", default))
        assertEquals(default, SourceRemoval.sharedTarget(moves))
    }

    @Test
    fun `no shared target when the apps move to different sources`() {
        val other = default.copy(uid = 2, name = "Other", isDefault = false)
        val moves = listOf(AppMovedOff("a", default), AppMovedOff("b", other))
        assertNull(SourceRemoval.sharedTarget(moves))
    }

    @Test
    fun `no apps name nobody`() {
        assertEquals(AppNameList(shown = emptyList(), others = 0), SourceRemoval.nameList(emptyList()))
    }

    @Test
    fun `one app is named alone`() {
        assertEquals(AppNameList(shown = listOf("YouTube"), others = 0), SourceRemoval.nameList(listOf("YouTube")))
    }

    @Test
    fun `two apps are both named`() {
        assertEquals(
            AppNameList(shown = listOf("YouTube", "Reddit"), others = 0),
            SourceRemoval.nameList(listOf("YouTube", "Reddit")),
        )
    }

    @Test
    fun `three or more apps name two and count the rest`() {
        assertEquals(
            AppNameList(shown = listOf("YouTube", "Reddit"), others = 1),
            SourceRemoval.nameList(listOf("YouTube", "Reddit", "Twitch")),
        )
        assertEquals(
            AppNameList(shown = listOf("YouTube", "Reddit"), others = 3),
            SourceRemoval.nameList(listOf("YouTube", "Reddit", "Twitch", "Spotify", "Maps")),
        )
    }
}
