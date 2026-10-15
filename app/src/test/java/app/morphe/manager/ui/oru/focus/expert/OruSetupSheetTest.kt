/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.oru.focus.expert

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OruSetupSheetTest {

    private fun setup(
        sourceLabel: String? = null,
        patchVersion: String? = null,
        channel: OruChannelSummary? = null,
        appVersion: String? = null,
        selectedPatches: Int? = null,
        patchTotal: Int = 0,
        installer: String? = null,
    ) = OruSetup(
        sourceLabel = sourceLabel,
        patchVersion = patchVersion,
        sourceChoosable = false,
        channel = channel,
        channelSource = null,
        appVersion = appVersion,
        selectedPatches = selectedPatches,
        patchTotal = patchTotal,
        installer = installer,
        hasBranding = false,
        hasSavedCopy = false,
    )

    @Test
    fun `the summary line follows source, channel, version, patches and installer`() {
        val parts = oruSetupParts(
            setup(
                sourceLabel = "Source A",
                patchVersion = "3",
                channel = OruChannelSummary.Stable,
                appVersion = "21.20.405",
                selectedPatches = 42,
                patchTotal = 50,
                installer = "Install",
            ),
        )
        assertEquals(
            listOf(
                OruSetupPart.Source("Source A", "3"),
                OruSetupPart.Channel(OruChannelSummary.Stable),
                OruSetupPart.AppVersion("21.20.405"),
                OruSetupPart.Patches(42, 50),
                OruSetupPart.Installer("Install"),
            ),
            parts,
        )
    }

    @Test
    fun `a part whose value is not known is left out`() {
        assertEquals(listOf(OruSetupPart.Installer("Install")), oruSetupParts(setup(installer = "Install")))
        assertTrue(oruSetupParts(setup()).isEmpty())
    }

    @Test
    fun `patches show only while a run is held`() {
        val parts = oruSetupParts(setup(sourceLabel = "Source A", patchTotal = 50))
        assertTrue(parts.none { it is OruSetupPart.Patches })
    }

    @Test
    fun `a sheet opened from the setup sheet returns to it, and the setup sheet closes to the focus`() {
        val host = OruExpertSheetHost()
        host.open(OruExpertSheet.Setup)
        host.open(OruExpertSheet.Installer, returnTo = OruExpertSheet.Setup)
        host.close()
        assertEquals(OruExpertSheet.Setup, host.shown)
        assertEquals(OruExpertSheet.Setup, host.sheet)
        host.close()
        assertNull(host.sheet)
    }

    @Test
    fun `close all leaves the focus whatever sheet was opened from another`() {
        val host = OruExpertSheetHost()
        host.open(OruExpertSheet.Setup)
        host.open(OruExpertSheet.Sources, returnTo = OruExpertSheet.Setup)
        host.closeAll()
        assertNull(host.sheet)
        host.close()
        assertNull(host.sheet)
    }
}
