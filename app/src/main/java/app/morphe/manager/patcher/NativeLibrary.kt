/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.patcher

import android.content.Context
import java.io.File

/**
 * The patcher runs in its own process, which does not search the app's native library directory, so the native
 * dex index is told where its library is. Never throws: without the library the patcher indexes classes in Kotlin.
 */
internal object NativeLibrary {
    private const val PROPERTY = "oru.native.lib"

    fun configure(context: Context) {
        runCatching {
            if (System.getProperty(PROPERTY) != null) return
            File(context.applicationInfo.nativeLibraryDir, "liboru_patcher.so").takeIf { it.exists() }
                ?.let { System.setProperty(PROPERTY, it.absolutePath) }
        }
    }
}
