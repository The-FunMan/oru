/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.manager

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Checks by reading the sources that the oru flavour cannot reach a Morphe-run host or service.
 * Runs without the Android toolchain; the paths are relative to the app module, which is where
 * unit tests run.
 */
class OruNoProjectHostTest {

    /** Names of Morphe-run hosts, endpoints and services, as they appear in source. */
    private val projectHost = Regex(
        "morphe\\.software|MORPHE_API_URL|MORPHE_WEBSITE_URL|COMMUNITY_PATCHES_URL|BLOCKED_SOURCES_URL|" +
            "MorpheAPI|FcmTopic|FirebaseMessaging|google_app_id"
    )

    private fun sourcesUnder(path: String): List<File> {
        val root = File(path)
        assertTrue(root.isDirectory, "Missing source directory ${root.absolutePath}")
        return root.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .toList()
    }

    @Test
    fun oruScreensNameNoProjectHost() {
        val offenders = sourcesUnder("src/main/java/app/morphe/manager/ui/oru").flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (projectHost.containsMatchIn(line)) "${file.name}:${index + 1}" else null
            }
        }

        assertTrue(offenders.isEmpty(), "Oru screens reference a Morphe-run host: $offenders")
    }

    @Test
    fun oruManifestDropsTheFcmService() {
        val manifest = File("src/oru/AndroidManifest.xml").readText()
        val removal = Regex(
            "<service\\s+android:name=\"app\\.morphe\\.manager\\.service\\.MorpheFcmService\"\\s+tools:node=\"remove\""
        )

        assertTrue(removal.containsMatchIn(manifest), "The oru overlay must remove MorpheFcmService")
    }

    @Test
    fun everyMorpheApiGateReadsTheFlavourFlag() {
        val gated = listOf(
            "src/main/java/app/morphe/manager/network/api/MorpheAPI.kt",
            "src/main/java/app/morphe/manager/domain/manager/DownloadUrlResolver.kt",
            "src/main/java/app/morphe/manager/domain/repository/BlocklistRepository.kt",
        )

        gated.forEach { path ->
            assertTrue(
                File(path).readText().contains("OruEndpoints.current.projectApiEnabled"),
                "$path must not reach Morphe's API in oru"
            )
        }
    }
}
