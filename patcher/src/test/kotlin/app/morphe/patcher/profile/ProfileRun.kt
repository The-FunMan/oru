/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.profile

import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.apk.ApkUtils
import app.morphe.patcher.apk.ApkUtils.applyTo
import app.morphe.patcher.patch.loadPatchesFromJar
import app.morphe.patcher.util.PhaseProfiler
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val TARGET_PACKAGE = "com.google.android.youtube"

/**
 * Desktop run of the patcher on one APK, following the steps of the manager's Session.
 *
 * Arguments: input APK, patch bundle (.mpp, read as a JAR), output APK, work directory, keystore file.
 * Patches are the bundle's default patches compatible with [TARGET_PACKAGE].
 * Run with `-Doru.profile=true` to get the phase log and counters.
 */
fun main(args: Array<String>) {
    require(args.size == 5) { "usage: ProfileRun <input.apk> <bundle.mpp> <output.apk> <work dir> <keystore>" }
    val (input, bundle, output, work, keystore) = args.map(::File)

    // Touching the profiler starts its clock, so the setup phase covers loading the bundle.
    PhaseProfiler.enabled

    val patches = loadPatchesFromJar(setOf(bundle)).filter { patch ->
        patch.default && patch.name != null &&
            (patch.compatibility == null || patch.compatibility!!.any { it.packageName == TARGET_PACKAGE })
    }.toSet()

    val tempDir = work.resolve("patcher").apply { mkdirs() }
    val patcher = Patcher(
        PatcherConfig(
            apkFile = input,
            temporaryFilesPath = tempDir,
            fileWorkspacePath = work.resolve("workspace").apply { mkdirs() },
        ),
    )
    patcher += patches
    println("selected patches: ${patches.size}")
    PhaseProfiler.mark("setup")

    runBlocking {
        patcher().collect { result ->
            result.exception?.let { throw it }
        }
    }

    val result = patcher.get()
    val patched = result.resources.resourcesApk ?: tempDir.resolve("result.apk").also {
        Files.copy(input.toPath(), it.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    result.applyTo(patched)
    PhaseProfiler.mark("write and align")

    ApkUtils.signApk(
        patched,
        output,
        "oru-profile",
        ApkUtils.KeyStoreDetails(keystore, "oru-profile", "oru-profile", "oru-profile"),
    )
    PhaseProfiler.mark("sign")
    PhaseProfiler.report()

    patcher.close()
}
