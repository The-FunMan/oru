/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.home

import android.content.pm.PackageInfo
import app.morphe.manager.ui.oru.focus.ActiveRun

/** The run the accessory shows: the batch run while one is active, else the single-app run, else none. */
fun oruHomeRunOf(batch: OruHomeRun?, single: OruHomeRun?): OruHomeRun? = batch ?: single

/**
 * The accessory of the single-app run [active], named like its card. [packageInfoOf] looks up the card's package info
 * by its key, which is how the accessory gets the icon. Null when no single-app run is going.
 */
fun oruSingleHomeRunOf(active: ActiveRun?, packageInfoOf: (String) -> PackageInfo?): OruHomeRun? {
    active ?: return null
    return OruHomeRun(
        packageName = active.packageName,
        appName = oruAppName(active.packageName, active.label),
        packageInfo = packageInfoOf(active.packageKey),
        progress = active.progress,
        done = active.done,
        total = active.total,
        focusKey = active.packageKey,
        ready = active.ready,
    )
}
