/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.manager

import app.morphe.manager.ui.viewmodel.ImportExportViewModel
import kotlin.test.*

class SettingsExportSecretsTest {
    private val settingsJson = ImportExportViewModel.settingsJson

    private val snapshot = PreferencesManager.SettingsSnapshot(
        keystoreAlias = "signing-alias",
        keystorePass = "key-password",
        keystorePassword = "store-password",
        gitHubPat = "ghp_example",
        includeGitHubPatInExports = true,
    )

    @Test
    fun `settings export never carries keystore fields`() {
        val exported = snapshot.forExport(SettingsSection.entries.toSet())
        val encoded = settingsJson.encodeToString(exported)

        assertNull(exported.keystoreAlias)
        assertNull(exported.keystorePass)
        assertNull(exported.keystorePassword)
        assertFalse(encoded.contains("keystore"), "keystore fields must not reach the file: $encoded")
        assertFalse(encoded.contains("signing-alias"), "alias must not reach the file: $encoded")
        assertFalse(encoded.contains("key-password"), "key password must not reach the file: $encoded")
        assertFalse(encoded.contains("store-password"), "store password must not reach the file: $encoded")
    }

    @Test
    fun `github token still follows its own opt in`() {
        val exported = snapshot.forExport(SettingsSection.entries.toSet())

        assertEquals("ghp_example", exported.gitHubPat)
        assertEquals(true, exported.includeGitHubPatInExports)
    }

    @Test
    fun `import still reads keystore fields from older exports`() {
        val older = """{"keystoreAlias":"signing-alias","keystorePass":"key-password"}"""
        val decoded = settingsJson.decodeFromString<PreferencesManager.SettingsSnapshot>(older)

        val restored = decoded.restrictedTo(SettingsSection.entries.toSet())

        assertEquals("signing-alias", restored.keystoreAlias)
        assertEquals("key-password", restored.keystorePass)
    }
}
