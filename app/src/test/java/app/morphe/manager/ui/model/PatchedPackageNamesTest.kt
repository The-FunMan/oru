/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.model

import app.morphe.manager.patcher.patch.CompatiblePackage
import app.morphe.manager.patcher.patch.Option
import app.morphe.manager.patcher.patch.PatchInfo
import kotlinx.collections.immutable.toImmutableList
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals

private const val YOUTUBE = "com.google.android.youtube"
private const val MUSIC = "com.google.android.apps.youtube.music"
private const val PATCHED_YOUTUBE = "app.morphe.android.youtube"
private const val PATCHED_MUSIC = "app.morphe.android.apps.youtube.music"
private const val REDDIT = "com.reddit.frontpage"

class PatchedPackageNamesTest {

    /** A patch for [packages] (null for a universal one) whose package name option ships [default]. */
    private fun patch(packages: List<String?>?, default: String?, withOption: Boolean = true) = PatchInfo(
        name = "Change package name",
        description = null,
        include = true,
        compatiblePackages = packages
            ?.map { CompatiblePackage(packageName = it, versions = null) }
            ?.toImmutableList(),
        options = if (withOption) {
            listOf<Option<*>>(
                Option(
                    title = PACKAGE_NAME_OPTION_KEY,
                    key = PACKAGE_NAME_OPTION_KEY,
                    description = "",
                    required = false,
                    type = typeOf<String>(),
                    default = default,
                    presets = null,
                    validator = { true },
                ),
            ).toImmutableList()
        } else {
            null
        },
    )

    @Test
    fun `a patch that renames one app gives that app the name its default ships`() {
        val names = patchedPackageNamesByApp(listOf(patch(listOf(YOUTUBE), PATCHED_YOUTUBE)))
        assertEquals(mapOf(YOUTUBE to setOf(PATCHED_YOUTUBE)), names)
    }

    @Test
    fun `a patch shared by several apps has one default for all of them, so it names none`() {
        val names = patchedPackageNamesByApp(listOf(patch(listOf(YOUTUBE, MUSIC), PATCHED_YOUTUBE)))
        assertEquals(emptyMap(), names)
    }

    @Test
    fun `a universal patch names no app`() {
        val names = patchedPackageNamesByApp(listOf(patch(null, PATCHED_YOUTUBE)))
        assertEquals(emptyMap(), names)
    }

    @Test
    fun `a patch that only takes an override, or ships a blank one, names no app`() {
        assertEquals(emptyMap(), patchedPackageNamesByApp(listOf(patch(listOf(YOUTUBE), null))))
        assertEquals(emptyMap(), patchedPackageNamesByApp(listOf(patch(listOf(YOUTUBE), " "))))
        assertEquals(emptyMap(), patchedPackageNamesByApp(listOf(patch(listOf(YOUTUBE), null, withOption = false))))
    }

    @Test
    fun `a default that is the app's own package is not a patched name`() {
        assertEquals(emptyMap(), patchedPackageNamesByApp(listOf(patch(listOf(YOUTUBE), YOUTUBE))))
    }

    @Test
    fun `the Default sentinel a clone patch ships is not a package name`() {
        assertEquals(emptyMap(), patchedPackageNamesByApp(listOf(patch(listOf(YOUTUBE), "Default"))))
    }

    @Test
    fun `the fallback names YouTube and YouTube Music the way the GmsCore patches do`() {
        assertEquals(
            listOf(PATCHED_YOUTUBE, "com.google.android.youtube.morphe"),
            fallbackPatchedPackageNames(YOUTUBE),
        )
        assertEquals(
            listOf(PATCHED_MUSIC, "com.google.android.apps.youtube.music.morphe"),
            fallbackPatchedPackageNames(MUSIC),
        )
    }

    @Test
    fun `the fallback names Reddit the way the clone patch does`() {
        assertEquals(listOf("com.reddit.frontpage.morphe"), fallbackPatchedPackageNames(REDDIT))
    }

    @Test
    fun `an installed patched copy is found when the option gives nothing`() {
        assertEquals(
            listOf(PATCHED_YOUTUBE),
            installedPatchedCopies(YOUTUBE, declaredNames = emptySet(), installedPackages = setOf(PATCHED_YOUTUBE)),
        )
        assertEquals(
            listOf(PATCHED_MUSIC),
            installedPatchedCopies(MUSIC, declaredNames = emptySet(), installedPackages = setOf(PATCHED_MUSIC)),
        )
        assertEquals(
            listOf("com.reddit.frontpage.morphe"),
            installedPatchedCopies(REDDIT, declaredNames = emptySet(), installedPackages = setOf("com.reddit.frontpage.morphe")),
        )
    }

    @Test
    fun `a stock install that is present wins over any patched copy`() {
        val installed = setOf(YOUTUBE, PATCHED_YOUTUBE)
        assertEquals(emptyList(), installedPatchedCopies(YOUTUBE, declaredNames = setOf(PATCHED_YOUTUBE), installedPackages = installed))
        assertEquals(
            emptyList(),
            installedPatchedCopies(REDDIT, declaredNames = emptySet(), installedPackages = setOf(REDDIT, "com.reddit.frontpage.morphe")),
        )
    }

    @Test
    fun `a candidate that is not installed is not found`() {
        assertEquals(emptyList(), installedPatchedCopies(YOUTUBE, declaredNames = emptySet(), installedPackages = emptySet()))
        assertEquals(
            listOf("com.google.android.youtube.morphe"),
            installedPatchedCopies(YOUTUBE, declaredNames = emptySet(), installedPackages = setOf("com.google.android.youtube.morphe")),
        )
    }

    @Test
    fun `a declared name is tried before the fallback, and only installed names are kept`() {
        val copies = installedPatchedCopies(
            YOUTUBE,
            declaredNames = setOf("app.zzz.youtube"),
            installedPackages = setOf(PATCHED_YOUTUBE, "app.zzz.youtube"),
        )
        assertEquals(listOf("app.zzz.youtube", PATCHED_YOUTUBE), copies)
        assertEquals(
            listOf(PATCHED_YOUTUBE),
            installedPatchedCopies(YOUTUBE, declaredNames = setOf("app.zzz.youtube"), installedPackages = setOf(PATCHED_YOUTUBE)),
        )
    }

    @Test
    fun `names are collected per app across patches`() {
        val names = patchedPackageNamesByApp(
            listOf(
                patch(listOf(YOUTUBE), PATCHED_YOUTUBE),
                patch(listOf(YOUTUBE), "app.example.youtube"),
                patch(listOf(MUSIC), "app.morphe.android.apps.youtube.music"),
            ),
        )
        assertEquals(
            mapOf(
                YOUTUBE to setOf(PATCHED_YOUTUBE, "app.example.youtube"),
                MUSIC to setOf("app.morphe.android.apps.youtube.music"),
            ),
            names,
        )
    }
}
