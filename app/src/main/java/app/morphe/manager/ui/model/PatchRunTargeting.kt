/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.model

import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.util.KnownApps
import app.morphe.manager.util.PatchSelection

/**
 * Option key a patch declares the package name it builds under. The manager has no way to know
 * which patch renames an app, but the name that patch was given is right there to be read.
 */
const val PACKAGE_NAME_OPTION_KEY = "packageName"

/** Whether this patch is one that builds the app under a package name of its own. */
val PatchInfo.declaresPackageName: Boolean
    get() = options?.any { it.key == PACKAGE_NAME_OPTION_KEY } == true

/** The shape of a package name, as the bundle's own clone patch checks one. Rules out the "Default" sentinel. */
private val PACKAGE_NAME_SHAPE = Regex("^[a-z]\\w*(\\.[a-z]\\w*)+$")

/**
 * The package names an app's patched builds are given, keyed by the stock package the app goes by.
 *
 * Read from the [PACKAGE_NAME_OPTION_KEY] default of each patch that renames one app: that default is the
 * name the patcher gives the app's builds, so it is the name an install of such a build carries. A patch
 * compatible with several apps has a single default for all of them, so it names none of them. A default
 * that is not a package name, such as the "Default" a clone patch ships to mean "the name the patch picks",
 * names nothing either.
 *
 * Most bundles declare no such option (the YouTube and YouTube Music GmsCore patches name their builds in
 * code), so [fallbackPatchedPackageNames] covers the rest.
 */
fun patchedPackageNamesByApp(patches: Iterable<PatchInfo>): Map<String, Set<String>> {
    val names = mutableMapOf<String, MutableSet<String>>()
    for (patch in patches) {
        val packages = patch.compatiblePackages?.mapNotNullTo(mutableSetOf()) { it.packageName } ?: continue
        val packageName = packages.singleOrNull() ?: continue
        val default = patch.options?.firstOrNull { it.key == PACKAGE_NAME_OPTION_KEY }?.default as? String
        if (default == null || default == packageName || !PACKAGE_NAME_SHAPE.matches(default)) continue
        names.getOrPut(packageName) { mutableSetOf() } += default
    }
    return names
}

private const val GOOGLE_APP_PREFIX = "com.google.android."
private const val MORPHE_GOOGLE_APP_PREFIX = "app.morphe.android."

/** The Google apps whose GmsCore support patch renames them, in the bundle's patch code. */
private val GMS_CORE_RENAMED_APPS = setOf(KnownApps.YOUTUBE, KnownApps.YOUTUBE_MUSIC)

/**
 * The package names a patched build of [stockPackageName] carries when no patch declares one in its options,
 * derived from the rules the Morphe patches apply in code (checked against patches-1.44.0-dev.4 and the
 * morphe-patches source):
 *
 * 1. GmsCore support renames YouTube and YouTube Music, replacing the leading `com.google.android.` with
 *    `app.morphe.android.`: `com.google.android.youtube` gives `app.morphe.android.youtube` and
 *    `com.google.android.apps.youtube.music` gives `app.morphe.android.apps.youtube.music`.
 * 2. Clone app is universal. With its package name left at its default it builds every app under
 *    `<stock>.morphe`, so Reddit's build is `com.reddit.frontpage.morphe`. Rule 2 also covers YouTube and
 *    YouTube Music when GmsCore support is not selected.
 *
 * Nothing here says whether a candidate is installed. Callers look only at candidates that are.
 */
fun fallbackPatchedPackageNames(stockPackageName: String): List<String> = buildList {
    if (stockPackageName in GMS_CORE_RENAMED_APPS && stockPackageName.startsWith(GOOGLE_APP_PREFIX)) {
        add(MORPHE_GOOGLE_APP_PREFIX + stockPackageName.removePrefix(GOOGLE_APP_PREFIX))
    }
    add("$stockPackageName.morphe")
}

/**
 * The patched copies of [stockPackageName] that are installed, in the order they are tried: the names the
 * bundles declare in their options first (sorted), then the names [fallbackPatchedPackageNames] gives.
 *
 * Nothing is looked up while the stock app itself is installed. That install is the app the card is for, and
 * a patched copy of it must not take the card over.
 */
fun installedPatchedCopies(
    stockPackageName: String,
    declaredNames: Set<String>,
    installedPackages: Set<String>
): List<String> {
    if (stockPackageName in installedPackages) return emptyList()
    return (declaredNames.sorted() + fallbackPatchedPackageNames(stockPackageName))
        .distinct()
        .filter { it != stockPackageName && it in installedPackages }
}

/**
 * Whether this patch names the app it builds without being asked to, so selecting it is by itself
 * enough to produce a clone.
 *
 * A patch that picks the name itself ships a default to fall back on, while one taking the name
 * only as an override leaves it unset and reads the app's own instead. Patch authors are free to
 * break that convention, so this carries no further than the label on a patch in the list.
 */
val PatchInfo.renamesByDefault: Boolean
    get() = options?.firstOrNull { it.key == PACKAGE_NAME_OPTION_KEY }?.default != null

/**
 * Whether a run builds a copy of the app rather than the install the app itself has.
 *
 * A package name of its own is what a copy needs, not what makes it one: patches rename an app
 * for reasons of their own, and such a build is still the app's only install. What separates the
 * two is who asked for the rename, and a patch that renames only ends up in [selection] when the
 * user selected it. A patch pulled in as a dependency renames just the same and is never in there.
 */
internal fun producesClone(
    originalPackageName: String,
    resultPackageName: String,
    selection: PatchSelection,
    declaresPackageName: (bundleUid: Int, patchName: String) -> Boolean
): Boolean = resultPackageName != originalPackageName &&
        selection.any { (bundleUid, patchNames) ->
            patchNames.any { declaresPackageName(bundleUid, it) }
        }

/**
 * A finished build that answers to a package the run was not aimed at, so installing it adds a
 * clone rather than updating the app it was built from.
 *
 * A patch names the app it builds in its own code, so this is read off the output once the run
 * is over and holds however the patch arrived at the name.
 *
 * @param replacesExisting Whether the device already has an install under [resultPackageName],
 *   which is the case the user has most reason to hear about: it is about to be overwritten.
 */
data class RenameWarning(
    val targetPackageName: String,
    val resultPackageName: String,
    val replacesExisting: Boolean
)

/**
 * The package a run reads its saved patches and options from.
 *
 * Patches belong to the install they were applied to, so rebuilding one reads what that install
 * was built with. Anything else reads the app itself: a run producing a separate install starts
 * from what the app was last patched with, and so does an install that predates configurations
 * of their own.
 */
internal fun configurationKey(
    originalPackageName: String,
    repatchedPackageName: String?,
    repatchedHasOwnConfiguration: Boolean
): String = when {
    repatchedPackageName == null || repatchedPackageName == originalPackageName -> originalPackageName
    repatchedHasOwnConfiguration -> repatchedPackageName
    else -> originalPackageName
}
