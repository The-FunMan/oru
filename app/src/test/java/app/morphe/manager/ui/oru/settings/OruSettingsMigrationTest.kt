/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.settings

import app.morphe.manager.patcher.runtime.PROCESS_RUNTIME_MEMORY_NOT_SET
import app.morphe.manager.worker.UpdateCheckInterval
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OruSettingsMigrationTest {

    @Test
    fun flagKeyIsStableAcrossBuilds() {
        assertEquals("oru_ux_v1_migrated", OruSettingsMigration.FLAG_KEY)
    }

    @Test
    fun runsOnceInOruOnly() {
        assertTrue(OruSettingsMigration.shouldRun(oruFlavour = true, alreadyMigrated = false))
        assertFalse(OruSettingsMigration.shouldRun(oruFlavour = true, alreadyMigrated = true))
    }

    @Test
    fun morpheFlavourNeverRuns() {
        assertFalse(OruSettingsMigration.shouldRun(oruFlavour = false, alreadyMigrated = false))
        assertFalse(OruSettingsMigration.shouldRun(oruFlavour = false, alreadyMigrated = true))
    }

    @Test
    fun migrationWritesTheDecidedDefaults() {
        assertEquals(false, OruSettingsMigration.DISABLE_PATCH_VERSION_COMPAT_CHECK)
        assertEquals(true, OruSettingsMigration.STRIP_UNUSED_NATIVE_LIBS)
    }

    @Test
    fun importedCompatOverrideIsIgnoredInOru() {
        assertFalse(OruSettingsMigration.importedCompatCheck(imported = true, oruFlavour = true))
        assertFalse(OruSettingsMigration.importedCompatCheck(imported = false, oruFlavour = true))
    }

    @Test
    fun importedCompatOverrideIsKeptInMorphe() {
        assertTrue(OruSettingsMigration.importedCompatCheck(imported = true, oruFlavour = false))
        assertFalse(OruSettingsMigration.importedCompatCheck(imported = false, oruFlavour = false))
    }

    // Second step: pins the settings that have no oru UI

    private fun pins(processRuntimeDefault: Boolean = true): OruSettingsMigration.V2Pins =
        assertNotNull(OruSettingsMigration.v2Pins(oruFlavour = true, alreadyMigrated = false, processRuntimeDefault = processRuntimeDefault))

    @Test
    fun v2FlagKeyIsNewAndDoesNotReuseTheFirstStep() {
        assertEquals("oru_ux_v2_migrated", OruSettingsMigration.V2_FLAG_KEY)
        assertFalse(OruSettingsMigration.V2_FLAG_KEY == OruSettingsMigration.FLAG_KEY)
    }

    @Test
    fun autoInstallIsPinnedOn() {
        assertTrue(pins().autoInstallWithShizuku)
    }

    @Test
    fun installerCustomComponentsAreCleared() {
        assertEquals(emptySet<String>(), pins().installerCustomComponents)
    }

    @Test
    fun installerHiddenComponentsAreCleared() {
        assertEquals(emptySet<String>(), pins().installerHiddenComponents)
    }

    @Test
    fun customFilePickerIsOff() {
        assertFalse(pins().useCustomFilePicker)
    }

    @Test
    fun updateCheckIntervalIsDaily() {
        assertEquals(UpdateCheckInterval.DAILY, pins().updateCheckInterval)
    }

    @Test
    fun unusedNativeLibsAreStripped() {
        assertTrue(pins().stripUnusedNativeLibs)
    }

    @Test
    fun processRuntimeFollowsTheDeviceDefaultWhenOn() {
        assertTrue(pins(processRuntimeDefault = true).useProcessRuntime)
    }

    @Test
    fun processRuntimeFollowsTheDeviceDefaultWhenOff() {
        assertFalse(pins(processRuntimeDefault = false).useProcessRuntime)
    }

    @Test
    fun memoryLimitIsUnsetSoTheDeviceLimitIsPicked() {
        assertEquals(PROCESS_RUNTIME_MEMORY_NOT_SET, pins().patcherProcessMemoryLimit)
    }

    @Test
    fun v2RunsOnceInOruOnly() {
        assertNotNull(OruSettingsMigration.v2Pins(oruFlavour = true, alreadyMigrated = false, processRuntimeDefault = true))
        assertNull(OruSettingsMigration.v2Pins(oruFlavour = true, alreadyMigrated = true, processRuntimeDefault = true))
    }

    @Test
    fun v2DoesNotRunInTheStockFlavour() {
        assertNull(OruSettingsMigration.v2Pins(oruFlavour = false, alreadyMigrated = false, processRuntimeDefault = true))
        assertNull(OruSettingsMigration.v2Pins(oruFlavour = false, alreadyMigrated = true, processRuntimeDefault = false))
    }

    @Test
    fun v2IsIdempotentOnceTheFlagIsWritten() {
        // The first run writes the flag, so a second start of the app finds nothing to write
        val first = OruSettingsMigration.v2Pins(oruFlavour = true, alreadyMigrated = false, processRuntimeDefault = true)
        val second = OruSettingsMigration.v2Pins(oruFlavour = true, alreadyMigrated = true, processRuntimeDefault = true)
        assertNotNull(first)
        assertNull(second)
    }
}
