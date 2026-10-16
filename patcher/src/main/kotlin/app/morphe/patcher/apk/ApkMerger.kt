/*
 *  Copyright (C) 2022 github.com/REAndroid
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// https://github.com/REAndroid/APKEditor/blob/ea49069ddbaf14bddbd23836082c2794815b18d1/src/main/java/com/reandroid/apkeditor/merge/Merger.java
package app.morphe.patcher.apk

import app.morphe.patcher.apk.ApkUtils.writeAlignedApk
import app.morphe.patcher.logging.ArsclibLogger
import app.morphe.patcher.logging.Logger
import app.morphe.patcher.logging.NoOpLogger
import app.morphe.patcher.util.FileUtils.safelyDelete
import com.reandroid.apk.ApkBundle
import com.reandroid.apk.ApkModule
import com.reandroid.app.AndroidManifest
import com.reandroid.archive.ArchiveEntry
import com.reandroid.archive.ArchiveFile
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.chunk.xml.ResXmlElement
import com.reandroid.arsc.value.ValueType
import com.reandroid.utils.HexUtil
import java.io.File
import java.io.IOException
import java.util.function.Predicate

@Suppress("unused")
class ApkMerger(
    private val logger: Logger = NoOpLogger
) {
    private val arsclibLogger = ArsclibLogger(logger)

    /**
     * @param cleanMetaInf Ignored: META-INF is always kept, so the signature block is retained.
     */
    fun merge(
        inputFile: File,
        outputFile: File,
        validateModules: Boolean = true,
        resDirName: String? = null,
        validateResDir: Boolean = true,
        extractNativeLibs: Boolean? = null,
        @Suppress("UNUSED_PARAMETER") cleanMetaInf: Boolean = false
    ) {
        outputFile.safelyDelete()
        var dir: File = inputFile
        var extracted = false
        val bundle = ApkBundle()
        var mergedModule: ApkModule? = null
        try {
            if (dir.isFile) {
                dir = extractFile(dir)
                extracted = true
            }
            logger.info("Searching apk files ...")
            bundle.setAPKLogger(arsclibLogger)
            bundle.loadApkDirectory(dir, extracted)
            logger.info("Found modules: " + bundle.apkModuleList.size)
            val module = bundle.mergeModules(validateModules).also { mergedModule = it }
            if (resDirName != null) {
                logger.info("Renaming resources root dir: $resDirName")
                module.setResourcesRootDir(resDirName)
            }
            if (validateResDir) {
                logger.info("Validating resources dir ...")
                module.validateResourcesDir()
            }
            sanitizeManifest(module)
            module.refreshTable()
            module.refreshManifest()
            val shouldExtractNativeLibs = extractNativeLibs ?: module.androidManifest?.isExtractNativeLibs
            logger.info("Setting extractNativeLibs=$shouldExtractNativeLibs")
            module.setExtractNativeLibs(shouldExtractNativeLibs)
            logger.info("Writing apk ...")
            module.writeAlignedApk(outputFile)
            logger.info("Saved to: $outputFile")
        } catch (t: Throwable) {
            outputFile.safelyDelete()
            throw t
        } finally {
            mergedModule?.close()
            bundle.close()
            if (extracted) dir.safelyDelete()
        }
    }

    @Throws(IOException::class)
    private fun extractFile(file: File): File {
        val tmp = toTmpDir(file)
        logger.info("Extracting to: $tmp")
        if (tmp.exists()) {
            logger.info("Delete: $tmp")
            tmp.safelyDelete()
        }
        val archive = ArchiveFile(file)
        fixFilePermissions(archive)
        val filter = Predicate { archiveEntry: ArchiveEntry? -> archiveEntry!!.name.endsWith(".apk") }
        val count = try {
            archive.extractAll(tmp, filter, arsclibLogger)
        } catch (t: Throwable) {
            tmp.safelyDelete()
            throw t
        } finally {
            archive.close()
        }
        if (count == 0) {
            throw IOException("No *.apk files found on: $file")
        }
        return tmp
    }

    private fun fixFilePermissions(archive: ArchiveFile) {
        val rwAll = 438 // equivalent to chmod 666
        val iterator = archive.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            entry.getCentralEntryHeader().filePermissions.permissions(rwAll)
        }
    }

    private fun toTmpDir(file: File): File {
        val name = HexUtil.toHex8("tmp_", file.name.hashCode())
        val tmp = file.parentFile?.let { File(it, name) } ?: File(name)
        return ApkEditorUtil.ensureUniqueFile(tmp)
    }

    private fun sanitizeManifest(apkModule: ApkModule) {
        if (!apkModule.hasAndroidManifest()) {
            return
        }
        val manifest = apkModule.getAndroidManifest()
        logger.info("Sanitizing manifest ...")

        AndroidManifestHelper.removeAttributeFromManifestById(
            manifest,
            AndroidManifest.ID_requiredSplitTypes, arsclibLogger
        )
        AndroidManifestHelper.removeAttributeFromManifestById(
            manifest,
            AndroidManifest.ID_splitTypes, arsclibLogger
        )
        AndroidManifestHelper.removeAttributeFromManifestByName(
            manifest,
            AndroidManifest.NAME_splitTypes, arsclibLogger
        )

        AndroidManifestHelper.removeAttributeFromManifestByName(
            manifest,
            AndroidManifest.NAME_requiredSplitTypes, arsclibLogger
        )
        AndroidManifestHelper.removeAttributeFromManifestByName(
            manifest,
            AndroidManifest.NAME_splitTypes, arsclibLogger
        )
        AndroidManifestHelper.removeAttributeFromManifestAndApplication(
            manifest,
            AndroidManifest.ID_isSplitRequired,
            arsclibLogger, AndroidManifest.NAME_isSplitRequired
        )
        // A pure split APK has no application element, so there is no split metadata to remove
        val application = manifest.getApplicationElement()
        val splitMetaDataElements: List<ResXmlElement> =
            application?.let { AndroidManifestHelper.listSplitRequired(it) }.orEmpty()
        var splitsRemoved = false
        for (meta in splitMetaDataElements) {
            if (!splitsRemoved) {
                splitsRemoved = removeSplitsTableEntry(meta, apkModule)
            }
            logger.info(
                ("Removed-element : <" + meta.name + "> name=\""
                        + AndroidManifestBlock.getAndroidNameValue(meta) + "\"")
            )
            application?.remove(meta)
        }
        manifest.refresh()
    }

    private fun removeSplitsTableEntry(metaElement: ResXmlElement, apkModule: ApkModule): Boolean {
        val nameAttribute = metaElement.searchAttributeByResourceId(AndroidManifest.ID_name) ?: return false
        if ("com.android.vending.splits" != nameAttribute.getValueAsString()) {
            return false
        }
        var valueAttribute = metaElement.searchAttributeByResourceId(
            AndroidManifest.ID_value
        )
        if (valueAttribute == null) {
            valueAttribute = metaElement.searchAttributeByResourceId(
                AndroidManifest.ID_resource
            )
        }
        if (valueAttribute == null
            || valueAttribute.valueType != ValueType.REFERENCE
        ) {
            return false
        }
        if (!apkModule.hasTableBlock()) {
            return false
        }
        val tableBlock = apkModule.tableBlock
        val resourceEntry = tableBlock.getResource(valueAttribute.data) ?: return false
        val zipEntryMap = apkModule.zipEntryMap
        for (entry in resourceEntry) {
            if (entry == null) {
                continue
            }
            val resValue = entry.getResValue() ?: continue
            val path = resValue.getValueAsString()
            logger.trace("Removed-table-entry : $path")
            //Remove file entry
            zipEntryMap.remove(path)
            // It's not safe to destroy entry, resource id might be used in dex code.
            // Better replace it with boolean value.
            entry.isNull = true
            val specTypePair = entry.typeBlock.parentSpecTypePair
            specTypePair.removeNullEntries(entry.getId())
        }
        return true
    }
}