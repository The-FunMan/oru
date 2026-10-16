/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.bundles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppSourceChoiceTest {

    private fun fact(
        uid: Int,
        name: String,
        isDefault: Boolean = false,
        path: String? = null,
        patches: Int = 25,
        recommended: String? = "21.20.405",
    ) = AppSourceFact(
        uid = uid,
        name = name,
        isDefault = isDefault,
        repositoryPath = path,
        patchVersion = "1.0.0",
        patchCount = patches,
        recommendedVersion = recommended,
    )

    private val default = fact(uid = 0, name = "Default patches", isDefault = true)
    private val remote = fact(
        uid = 4821,
        name = "Community patches",
        path = "owner/community",
        patches = 7,
        recommended = "21.16.256",
    )

    @Test
    fun `the source the app was turned on for wins over the pre-installed one`() {
        // The default is turned down for the app: the user picked the remote source for it
        val effective = AppSourceChoice.effective(listOf(default, remote), turnedDown = setOf(0))
        assertEquals(remote.uid, effective?.uid)
    }

    @Test
    fun `without a choice the pre-installed source is used when it supports the app`() {
        assertEquals(0, AppSourceChoice.effective(listOf(remote, default), turnedDown = emptySet())?.uid)
    }

    @Test
    fun `the only source that supports the app is used`() {
        assertEquals(remote.uid, AppSourceChoice.effective(listOf(remote), turnedDown = emptySet())?.uid)
    }

    @Test
    fun `with no default among them the lowest uid is used, whatever the order they are listed in`() {
        val a = fact(uid = 900, name = "Alpha")
        val b = fact(uid = 17, name = "Zulu")
        assertEquals(17, AppSourceChoice.effective(listOf(a, b), emptySet())?.uid)
        assertEquals(17, AppSourceChoice.effective(listOf(b, a), emptySet())?.uid)
    }

    @Test
    fun `when every source is turned down the app still has one to patch from`() {
        assertEquals(0, AppSourceChoice.effective(listOf(default, remote), setOf(0, remote.uid))?.uid)
    }

    @Test
    fun `no sources means no effective source`() {
        assertNull(AppSourceChoice.effective(emptyList(), emptySet()))
    }

    @Test
    fun `a source added after the choice does not take the app over`() {
        // The app is patched from the remote source; the default is turned down for it
        val before = listOf(default, remote)
        val chosen = AppSourceChoice.effective(before, turnedDown = setOf(0))?.uid

        // A new source with a lower uid, which the lowest-uid rule would otherwise pick. It patches the app too, so
        // the add turns it down for the app, as the Home does when a source is added
        val youtube = "com.google.android.youtube"
        val added = fact(uid = 3, name = "Morphe Patches", path = "Morphe/patches")
        val turnedDownForAdded = AppSourceChoice.appsToTurnDown(setOf(youtube), listOf(setOf(youtube)))
        assertEquals(setOf(youtube), turnedDownForAdded)

        // The app's turned-down sources after the add: the default, and the new source where it was turned down
        val turnedDownAfter = setOf(0) + if (youtube in turnedDownForAdded) setOf(added.uid) else emptySet()
        val after = AppSourceChoice.effective(before + added, turnedDown = turnedDownAfter)
        assertEquals(chosen, after?.uid)
    }

    @Test
    fun `a source added for an app only it covers becomes that app's source`() {
        val apps = setOf("com.reddit.frontpage")
        assertTrue(AppSourceChoice.appsToTurnDown(apps, listOf(setOf("com.google.android.youtube"))).isEmpty())
    }

    @Test
    fun `a choice is shown only when two or more sources patch the app`() {
        assertFalse(AppSourceChoice.offersChoice(emptyList()))
        assertFalse(AppSourceChoice.offersChoice(listOf(remote)))
        assertTrue(AppSourceChoice.offersChoice(listOf(default, remote)))
    }

    @Test
    fun `a source with fewer patches than the default one is flagged`() {
        val partial = fact(uid = 5, name = "Partial", path = "owner/partial", patches = 5)
        assertTrue(AppSourceChoice.coversFewerThanDefault(partial, listOf(default, partial)))
    }

    @Test
    fun `a source with as many patches as the default one is not flagged`() {
        val same = fact(uid = 5, name = "Same", path = "owner/same", patches = 25)
        assertFalse(AppSourceChoice.coversFewerThanDefault(same, listOf(default, same)))
    }

    @Test
    fun `the default source is never flagged, nor a source when there is no default`() {
        assertFalse(AppSourceChoice.coversFewerThanDefault(default, listOf(default, remote)))
        assertFalse(AppSourceChoice.coversFewerThanDefault(remote, listOf(remote)))
    }

    @Test
    fun `a remote source is named by its owner and repository, from its address`() {
        assertEquals(
            "Morphe/patches",
            AppSourceChoice.repositoryPathOf("https://raw.githubusercontent.com/Morphe/patches/main/patches.json"),
        )
        assertEquals("example.com", AppSourceChoice.repositoryPathOf("https://example.com/"))
    }
}
