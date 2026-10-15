/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.manager

import app.morphe.manager.util.ORU_RELEASE_REPO
import app.morphe.manager.util.ORU_REPO_URL
import app.morphe.manager.util.OruFileNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The identity of the Oru build in one place: the release repository, the release file names, the
 * keystore alias and the exported file prefix. A rename of the product changes these constants and
 * this test together.
 */
class OruIdentityTest {

    @Test
    fun releasesComeFromTheOruRepository() {
        assertEquals("The-FunMan/oru", ORU_RELEASE_REPO)
        assertEquals("https://github.com/The-FunMan/oru", ORU_REPO_URL)
        assertEquals(
            "https://api.github.com/repos/The-FunMan/oru/releases/latest",
            ORU_LATEST_FEED_URL,
        )
        assertEquals(
            "https://api.github.com/repos/The-FunMan/oru/releases?per_page=30",
            ORU_RELEASES_FEED_URL,
        )
    }

    @Test
    fun apkNameIsOruDashVersion() {
        assertEquals("oru-0.0.1.apk", oruApkName("v0.0.1"))
        assertEquals("oru-0.0.1.apk", oruApkName("0.0.1"))
        assertEquals("oru-1.0.0-dev.2.apk", oruApkName("v1.0.0-dev.2"))
    }

    @Test
    fun downloadsAreAcceptedOnlyFromTheOruRepository() {
        assertTrue(isOruReleaseDownload("$ORU_REPO_URL/releases/download/v0.0.1/oru-0.0.1.apk"))
        assertFalse(isOruReleaseDownload("https://github.com/MorpheApp/morphe-manager/releases/download/v0.0.1/a.apk"))
    }

    @Test
    fun newKeystoresUseTheOruAlias() {
        assertEquals("oru", OruEndpoints(upstreamServices = false).newKeystoreAlias)
    }

    @Test
    fun exportsUseTheOruPrefixAndKeystoreName() {
        assertEquals("oru", OruFileNames.prefix(oruUx = true))
        assertEquals("Oru.keystore", OruFileNames.keystore(oruUx = true))
        assertTrue("oru" in OruFileNames.extraKeystoreSecrets(oruUx = true))
    }
}
