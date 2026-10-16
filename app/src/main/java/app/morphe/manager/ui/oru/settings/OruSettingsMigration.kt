/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.settings

import app.morphe.manager.patcher.runtime.PROCESS_RUNTIME_MEMORY_NOT_SET
import app.morphe.manager.worker.UpdateCheckInterval

/**
 * One-time defaults that the oru flavour applies to stores created by earlier builds. The
 * morphe flavour never runs them, so its stored values stay as they are.
 *
 * [PreferencesManager] calls [shouldRun] once per step from its init block and writes the values
 * the step returns when it runs. No DataStore key is removed.
 */
object OruSettingsMigration {

    /** Stored flag that marks the first oru one-time step as done. */
    const val FLAG_KEY = "oru_ux_v1_migrated"

    /**
     * Stored flag for the second step, which pins the settings the oru UI no longer shows. It has
     * its own key, so the first step's flag is never reused.
     */
    const val V2_FLAG_KEY = "oru_ux_v2_migrated"

    /** The imported compatibility override is off in oru. */
    const val DISABLE_PATCH_VERSION_COMPAT_CHECK = false

    /** Unused native libraries are stripped in oru. */
    const val STRIP_UNUSED_NATIVE_LIBS = true

    /**
     * The values the second oru step writes. Each one is a setting that has no oru UI any more
     * (SETTINGS_IA rows 08, 18-20, 38-40 and 47). The old tabs could change most of them, so a
     * stored value from them must not keep changing oru behaviour.
     */
    data class V2Pins(
        val autoInstallWithShizuku: Boolean,
        val installerCustomComponents: Set<String>,
        val installerHiddenComponents: Set<String>,
        val useCustomFilePicker: Boolean,
        val updateCheckInterval: UpdateCheckInterval,
        val stripUnusedNativeLibs: Boolean,
        val useProcessRuntime: Boolean,
        /** [PROCESS_RUNTIME_MEMORY_NOT_SET] makes the next launch pick the limit for the device. */
        val patcherProcessMemoryLimit: Int,
    )

    /** True only in the oru flavour, and only until the flag has been written once. */
    fun shouldRun(oruFlavour: Boolean, alreadyMigrated: Boolean): Boolean =
        oruFlavour && !alreadyMigrated

    /**
     * The writes for the second step, or null when it must not run. [processRuntimeDefault] is the
     * device default for process runtime, passed in so the rule can be tested without a device.
     */
    fun v2Pins(oruFlavour: Boolean, alreadyMigrated: Boolean, processRuntimeDefault: Boolean): V2Pins? {
        if (!shouldRun(oruFlavour, alreadyMigrated)) return null
        return V2Pins(
            // Owner decision (row 18): install with no tap when a silent installer is available
            autoInstallWithShizuku = true,
            installerCustomComponents = emptySet(),
            installerHiddenComponents = emptySet(),
            useCustomFilePicker = false,
            updateCheckInterval = UpdateCheckInterval.DAILY,
            stripUnusedNativeLibs = STRIP_UNUSED_NATIVE_LIBS,
            useProcessRuntime = processRuntimeDefault,
            patcherProcessMemoryLimit = PROCESS_RUNTIME_MEMORY_NOT_SET,
        )
    }

    /**
     * The value a settings import stores for the compatibility override. Oru keeps it off, so a
     * backup made with the override on cannot switch the check off again.
     */
    fun importedCompatCheck(imported: Boolean, oruFlavour: Boolean): Boolean =
        imported && !oruFlavour
}
