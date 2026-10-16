/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.installer

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The stock APK is copied by root and installed from a path other apps cannot plant a file at. */
class RootInstallerStockInstallCommandTest {
    private val hash = "a".repeat(64)

    @Test
    fun `the staging directory is a fresh root-only directory, not a fixed name in tmp`() {
        val command = RootInstaller.stockInstallCommand("/data/app/base.apk", hash, allowDowngrade = false)

        assertContains(command, "dir=/data/local/tmp/morphe-stock-\$(cat /proc/sys/kernel/random/uuid)")
        assertContains(command, "mkdir -m 700 \"\$dir\"")
    }

    @Test
    fun `the copy is checked against the expected hash before pm install runs`() {
        val command = RootInstaller.stockInstallCommand("/data/app/base.apk", hash, allowDowngrade = false)

        val check = command.indexOf("sha256sum")
        val install = command.indexOf("pm install")
        assertTrue(check in 0 until install, "hash check must come before pm install")
        assertContains(command, "= '$hash'")
    }

    @Test
    fun `a plain install does not allow a downgrade`() {
        val command = RootInstaller.stockInstallCommand("/data/app/base.apk", hash, allowDowngrade = false)

        assertContains(command, "pm install -r \"\$dir/stock.apk\"")
        assertFalse(command.contains(" -d "))
    }

    @Test
    fun `a downgrade install allows the downgrade explicitly`() {
        val command = RootInstaller.stockInstallCommand("/data/app/base.apk", hash, allowDowngrade = true)

        assertContains(command, "pm install -r -d \"\$dir/stock.apk\"")
    }

    @Test
    fun `the source path is quoted for the shell`() {
        val command = RootInstaller.stockInstallCommand("/data/app/it's/base.apk", hash, allowDowngrade = false)

        assertContains(command, "cp '/data/app/it'\"'\"'s/base.apk' ")
    }

    @Test
    fun `the commands run in a subshell so exit does not close the root shell`() {
        val command = RootInstaller.stockInstallCommand("/data/app/base.apk", hash, allowDowngrade = false)

        assertTrue(command.trimStart().startsWith("("))
        assertTrue(command.trimEnd().endsWith(")"))
    }
}
