package app.morphe.patcher.resource.coder

import app.morphe.patcher.resource.CpuArchitecture
import java.io.File

/**
 * Keeps the coders a test opened so [close] can release them. A coder holds the input APK open until it is
 * closed, which on Windows keeps the test directory from being deleted.
 */
internal class CoderRegistry {
    private val coders = mutableListOf<ArsclibResourceCoder>()

    fun open(workingDir: File, apkFile: File, keepArchitectures: Set<CpuArchitecture> = emptySet()) =
        ArsclibResourceCoder(workingDir, apkFile, keepArchitectures).also { coders += it }

    fun close() {
        coders.forEach { it.close() }
        coders.clear()
    }
}
