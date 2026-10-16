/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/patcher/runtime/process/PatcherProcess.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.patcher.runtime.process

import android.annotation.SuppressLint
import android.app.ActivityThread
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.PowerManager
import app.morphe.manager.BuildConfig
import app.morphe.manager.patcher.Session
import app.morphe.manager.patcher.logger.LogLevel
import app.morphe.manager.patcher.logger.Logger
import app.morphe.manager.patcher.patch.PatchBundle
import app.morphe.manager.patcher.patch.applyPatchOptions
import app.morphe.manager.patcher.runtime.ProcessRuntime
import app.morphe.manager.patcher.runtime.ResourceMonitor
import app.morphe.manager.patcher.runtime.heapLimitMebibytes
import app.morphe.manager.patcher.split.SplitApkPreparer
import app.morphe.manager.patcher.split.SplitPreparationEvent
import app.morphe.manager.ui.model.StepId
import app.morphe.manager.ui.model.State
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.system.exitProcess

/**
 * The main class that runs inside the runner process launched by [ProcessRuntime].
 */
class PatcherProcess(private val context: Context) : IPatcherProcess.Stub() {
    private var eventBinder: IPatcherEvents? = null

    private val scope =
        CoroutineScope(Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
            // Try to send the exception information to the main app
            eventBinder?.let {
                try {
                    it.finished(truncateForIpc(throwable.stackTraceToString()))
                    return@CoroutineExceptionHandler
                } catch (_: Exception) {
                }
            }

            throwable.printStackTrace()
            exitProcess(1)
        })

    override fun buildId() = BuildConfig.BUILD_ID
    override fun exit() = exitProcess(0)

    override fun start(parameters: Parameters, events: IPatcherEvents) {
        eventBinder = events

        scope.launch {
            val logger = object : Logger() {
                override fun log(level: LogLevel, message: String) =
                    events.log(level.name, truncateForIpc(message))
            }

            ResourceMonitor.startPolling(logger)

            val heapLimitMb = heapLimitMebibytes()
            logger.info("$LOG_PROCESS_PREFIX_PROCESS_HEAP ${heapLimitMb}MB")
            events.heapLimit(heapLimitMb)

            // Bundle loading and split preparation share only the package name, so they run together
            val (patchList, preparation) = loadAndPrepareConcurrently(
                load = {
                    val allPatches = PatchBundle.Loader.patches(parameters.configurations.map { it.bundle }, parameters.packageName)
                    val patchList = parameters.configurations.flatMap { config ->
                        val bundlePatches = allPatches[config.bundle] ?: return@flatMap emptyList()

                        bundlePatches.applyPatchOptions(config.options, logger)

                        bundlePatches.filterKeys { it in config.patches }.values
                    }

                    events.progress(null, State.COMPLETED.name, null) // Loading patches
                    patchList
                },
                prepare = {
                    SplitApkPreparer.prepareIfNeeded(
                        source = File(parameters.inputFile),
                        workspace = File(parameters.cacheDir),
                        logger = logger,
                        skipUnneededSplits = parameters.stripUnusedNativeLibs,
                        onEvent = { event ->
                            // Forward raw event over IPC; main process resolves the localized
                            // label and logs it so the app locale is used, not the system locale
                            events.splitProgress(event.wireType, (event as? SplitPreparationEvent.Merging)?.apkName)
                        }
                    )
                },
                cleanup = { it.cleanup() }
            )

            val failure = try {
                // A merged APK is patched from the agreed path, so ProcessRuntime can read it back
                // in the main process after this one exits. Both sit in the cache directory, which
                // makes this a rename instead of rewriting the whole file
                val input = parameters.mergedInputFile
                    ?.takeIf { preparation.merged }
                    ?.let { dest ->
                        Files.move(
                            preparation.file.toPath(),
                            Paths.get(dest),
                            StandardCopyOption.REPLACE_EXISTING
                        ).toFile()
                    }
                    ?: preparation.file

                if (preparation.merged) {
                    events.progress(null, State.COMPLETED.name, null)
                }

                Session(
                    cacheDir = parameters.cacheDir,
                    frameworkDir = parameters.frameworkDir,
                    androidContext = context,
                    logger = logger,
                    input = input,
                    stripUnusedNativeLibs = parameters.stripUnusedNativeLibs,
                    onPatchCompleted = { patchName -> events.patchSucceeded(patchName) },
                    onPatchFailed = { patchName -> events.patchFailed(patchName) },
                    onProgress = { name, state, message ->
                        events.progress(name, state?.name, message)
                    },
                    onWriteFraction = { fraction ->
                        events.stageFraction(StepId.WRITE_PATCHED_APK.name, fraction)
                    }
                ).use {
                    it.run(File(parameters.outputFile), patchList)
                }
                null
            } catch (e: Exception) {
                e
            } finally {
                // Before reporting back: the manager answers finished() with exit(), which ends
                // this process without running anything still pending here
                preparation.cleanup()
            }
            ResourceMonitor.stopPolling(logger)
            events.finished(failure?.stackTraceToString()?.let(::truncateForIpc))
        }
    }


    companion object {
        const val LOG_PROCESS_PREFIX_PROCESS_HEAP = "Process heap memory limit:"

        private val longArrayClass = LongArray::class.java
        private val emptyLongArray = LongArray(0)

        @SuppressLint("PrivateApi")
        @JvmStatic
        fun main(args: Array<String>) {
            @Suppress("DEPRECATION")
            Looper.prepareMainLooper()

            val managerPackageName = args[0]

            // Abuse hidden APIs to get a context.
            val systemContext = ActivityThread.systemMain().systemContext as Context
            val appContext = systemContext.createPackageContext(managerPackageName, 0)

            // Keep the CPU awake for the duration of patching. The child process does not
            // inherit the parent's WakeLock, so it needs its own. Released automatically
            // when the process exits via any exitProcess() call.
            @Suppress("WakelockTimeout")
            (appContext.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PatcherProcess::Patcher")
                .acquire()

            // Avoid annoying logs. See https://github.com/robolectric/robolectric/blob/ad0484c6b32c7d11176c711abeb3cb4a900f9258/robolectric/src/main/java/org/robolectric/android/internal/AndroidTestEnvironment.java#L376-L388
            Class.forName("android.app.AppCompatCallbacks").apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    getDeclaredMethod("install", longArrayClass, longArrayClass).also { it.isAccessible = true }(null, emptyLongArray, emptyLongArray)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    getDeclaredMethod("install", longArrayClass).also { it.isAccessible = true }(null, emptyLongArray)
                }
            }

            val ipcInterface = PatcherProcess(appContext)

            appContext.sendBroadcast(Intent().apply {
                action = ProcessRuntime.CONNECT_TO_APP_ACTION
                `package` = managerPackageName

                putExtra(ProcessRuntime.INTENT_BUNDLE_KEY, Bundle().apply {
                    putBinder(ProcessRuntime.BUNDLE_BINDER_KEY, ipcInterface.asBinder())
                })
            })

            Looper.loop()
            exitProcess(1) // Shouldn't happen
        }
    }
}

/** Largest text, in UTF-8 bytes, sent in one oneway event call. Longer text keeps its head. */
internal const val MAX_IPC_TEXT_BYTES = 16 * 1024

private const val TRUNCATED_MARKER = "...(truncated)"

/**
 * Returns [text] unchanged when it fits in [maxBytes] UTF-8 bytes. Otherwise keeps the longest
 * prefix of whole code points that leaves room for [TRUNCATED_MARKER] and appends the marker.
 */
internal fun truncateForIpc(text: String, maxBytes: Int = MAX_IPC_TEXT_BYTES): String {
    if (text.toByteArray(Charsets.UTF_8).size <= maxBytes) {
        return text
    }

    val budget = maxBytes - TRUNCATED_MARKER.toByteArray(Charsets.UTF_8).size
    val head = StringBuilder()
    var used = 0
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        val size = when {
            codePoint < 0x80 -> 1
            codePoint < 0x800 -> 2
            codePoint < 0x10000 -> 3
            else -> 4
        }
        if (used + size > budget) {
            break
        }
        head.appendCodePoint(codePoint)
        used += size
        index += Character.charCount(codePoint)
    }
    return head.append(TRUNCATED_MARKER).toString()
}

/**
 * Runs [load] and [prepare] at the same time and returns both results once both have finished.
 * A failure in either one cancels the other and rethrows that failure. A [prepare] result that
 * is not returned, because [load] failed after it completed, is passed to [cleanup].
 */
internal suspend fun <P, R : Any> loadAndPrepareConcurrently(
    load: suspend () -> P,
    prepare: suspend () -> R,
    cleanup: (R) -> Unit
): Pair<P, R> {
    // Written by the prepare child, which coroutineScope joins before it returns or throws
    var prepared: R? = null
    try {
        return coroutineScope {
            val loading = async { load() }
            val preparing = async { prepare().also { prepared = it } }
            loading.await() to preparing.await()
        }
    } catch (e: Throwable) {
        prepared?.let(cleanup)
        throw e
    }
}
