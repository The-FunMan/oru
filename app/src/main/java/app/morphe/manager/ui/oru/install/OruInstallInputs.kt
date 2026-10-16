/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.install

import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.installer.SessionInstaller

/**
 * Adapters from the installer types to the planner's pure inputs. The planner itself never sees
 * these Android-facing types, so it stays testable on the JVM.
 */

/** Maps a resolved installer token (from `InstallerManager.parseToken`) to the planner's choice. */
fun InstallerManager.Token.toInstallerChoice(): InstallerChoice = when (this) {
    InstallerManager.Token.Internal -> InstallerChoice.SYSTEM
    InstallerManager.Token.PlayStore -> InstallerChoice.PLAY_STORE
    InstallerManager.Token.Shizuku -> InstallerChoice.SHIZUKU
    InstallerManager.Token.ShizukuPlayStore -> InstallerChoice.SHIZUKU_PLAY_STORE
    // parseToken maps the legacy "root" value to AutoSaved, which is root mount
    InstallerManager.Token.AutoSaved -> InstallerChoice.ROOT
    InstallerManager.Token.RootPlayStore -> InstallerChoice.ROOT_PLAY_STORE
    InstallerManager.Token.None -> InstallerChoice.NONE
    is InstallerManager.Token.Component -> InstallerChoice.CUSTOM
}

/**
 * Maps Shizuku's status to the planner's state. The branch order matches
 * `SessionInstaller.shizukuStatus`, so the first failing check is the reason shown.
 */
fun SessionInstaller.ShizukuStatus.toShizukuState(): ShizukuState = when {
    !installed -> ShizukuState.NOT_INSTALLED
    !supported -> ShizukuState.UNSUPPORTED
    !running -> ShizukuState.NOT_RUNNING
    !permissionGranted -> ShizukuState.PERMISSION_MISSING
    else -> ShizukuState.READY
}
