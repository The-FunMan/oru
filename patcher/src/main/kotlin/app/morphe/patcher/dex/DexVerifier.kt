/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.dex

import java.io.File

interface DexVerifier {
    fun verifyDexFile(dexFile: File)
    fun verifyDexDirectory(dexDir: File)
    fun verifyApkFile(apkFile: File)
}

object NoOpDexVerifier : DexVerifier {
    override fun verifyDexFile(dexFile: File) {
        require(dexFile.isFile) { "DEX file does not exist" }
    }

    override fun verifyDexDirectory(dexDir: File) {
        require(dexDir.isDirectory) { "DEX directory does not exist" }
        dexDir.listFiles()?.forEach { verifyDexFile(it) }
    }

    override fun verifyApkFile(apkFile: File) {
        require(apkFile.isFile) { "APK file does not exist" }
    }
}

/**
 * Thrown when DEX verification reports a failure.
 */
class DexVerificationException(message: String) : RuntimeException(message)
