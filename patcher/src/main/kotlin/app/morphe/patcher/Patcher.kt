/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 *
 * Original forked code:
 * https://github.com/LisoUseInAIKyrios/revanced-patcher
 */

package app.morphe.patcher

import app.morphe.patcher.dex.BytecodeMode
import app.morphe.patcher.patch.*
import app.morphe.patcher.resource.ResourceMode
import app.morphe.patcher.util.PhaseProfiler
import app.morphe.patcher.util.overlapFitsHeap
import app.morphe.patcher.util.runInParallel
import kotlinx.coroutines.flow.flow
import java.io.Closeable
import java.util.logging.Logger

/**
 * A Patcher.
 *
 * @param config The configuration to use for the patcher.
 */
class Patcher(private val config: PatcherConfig) : Closeable {
    private val logger = Logger.getLogger(this::class.java.name)

    /**
     * The context containing the current state of the patcher.
     */
    val context = PatcherContext(config)

    /**
     * Receives the progress of [get] through the write stage, see [WriteProgressListener]. Null by default, in which
     * case nothing is reported.
     */
    var writeProgress: WriteProgressListener? = null

    /**
     * Add patches.
     *
     * @param patches The patches to add.
     */
    operator fun plusAssign(patches: Set<Patch<*>>) {
        // Add all patches to the executablePatches set.
        context.executablePatches += patches

        // Add all patches and their dependencies to the allPatches set.
        patches.forEach { patch ->
            fun Patch<*>.addRecursively() =
                also(context.allPatches::add).dependencies.forEach(Patch<*>::addRecursively)

            patch.addRecursively()
        }

        context.allPatches.let { allPatches ->
            // Check, if what kind of resource mode is required.
            config.resourceMode = if (allPatches.any { patch -> patch.anyRecursively { it is ResourcePatch } }) {
                ResourceMode.FULL
            } else if (allPatches.any { patch -> patch.anyRecursively { it is RawResourcePatch } }) {
                ResourceMode.RAW_ONLY
            } else {
                ResourceMode.NONE
            }

            config.bytecodeMode = if (allPatches.any { patch -> patch.anyRecursively { it is BytecodePatch } }) {
                config.useBytecodeMode
            } else {
                BytecodeMode.NONE
            }
        }
    }

    /**
     * Execute added patches.
     *
     * @return A flow of [PatchResult]s.
     */
    operator fun invoke() = flow {
        fun Patch<*>.execute(
            executedPatches: LinkedHashMap<Patch<*>, PatchResult>,
            inProgress: LinkedHashSet<Patch<*>>,
        ): PatchResult {
            // If the patch was executed before or failed, return it's the result.
            executedPatches[this]?.let { patchResult ->
                patchResult.exception ?: return patchResult

                return PatchResult(this, PatchException("The patch '$this' failed previously"))
            }

            // A patch that is still waiting on its dependencies is reached again only through a cycle
            if (!inProgress.add(this)) {
                val cycle = inProgress.dropWhile { it != this } + this
                return PatchResult(
                    this,
                    PatchException("Dependency cycle between patches: ${cycle.joinToString(" -> ") { "\"$it\"" }}"),
                )
            }

            try {
                // Recursively execute all dependency patches.
                dependencies.forEach { dependency ->
                    dependency.execute(executedPatches, inProgress).exception?.let {
                        return PatchResult(
                            this,
                            PatchException(
                                "The patch \"$this\" depends on \"$dependency\", which raised an exception",
                                it,
                            ),
                        )
                    }
                }

                // Execute the patch.
                val trace = PhaseProfiler.beginPatch()
                return try {
                    execute(context)

                    PatchResult(this)
                } catch (exception: PatchException) {
                    PatchResult(this, exception)
                } catch (exception: Exception) {
                    PatchResult(this, PatchException(exception))
                }.also {
                    executedPatches[this] = it
                    PhaseProfiler.endPatch("$this", trace)
                }
            } finally {
                inProgress.remove(this)
            }
        }

        // Prevent decoding the app manifest twice if it is not needed.
        if (config.resourceMode != ResourceMode.NONE) {
            context.resourceContext.decodeResources(config.resourceMode)
        }
        PhaseProfiler.mark("resource decode")

        if (config.bytecodeMode != BytecodeMode.NONE) {
            context.bytecodeContext.decodeDexFiles()
        }
        PhaseProfiler.mark("dex decode and index")

        logger.info("Executing patches")

        val executedPatches = LinkedHashMap<Patch<*>, PatchResult>()

        context.executablePatches.sortedBy { it.name }.forEach { patch ->
            val patchResult = patch.execute(executedPatches, LinkedHashSet())

            // If an exception occurred or the patch has no finalize block, emit the result.
            if (patchResult.exception != null || patch.finalizeBlock == null) {
                emit(patchResult)
            }
        }

        val succeededPatchesWithFinalizeBlock = executedPatches.values.filter {
            it.exception == null && it.patch.finalizeBlock != null
        }

        succeededPatchesWithFinalizeBlock.asReversed().forEach { executionResult ->
            val patch = executionResult.patch

            val result =
                try {
                    patch.finalize(context)

                    executionResult
                } catch (exception: PatchException) {
                    PatchResult(patch, exception)
                } catch (exception: Exception) {
                    PatchResult(patch, PatchException(exception))
                }

            if (result.exception != null) {
                emit(
                    PatchResult(
                        patch,
                        PatchException(
                            "The patch \"$patch\" raised an exception",
                            result.exception,
                        ),
                    ),
                )
            } else if (patch in context.executablePatches) {
                emit(result)
            }
        }

        PhaseProfiler.mark("patch execution")
    }

    override fun close() = context.close()

    /**
     * Compile and save patched APK files.
     *
     * @return The [PatcherResult] containing the patched APK files.
     */
    @OptIn(InternalApi::class)
    fun get(): PatcherResult {
        Fingerprint.clearFingerprints()
        context.allPatches.clear()
        context.executablePatches.clear()
        // Patches have run, and compiling the output looks up no resource ids
        context.resourceContext.resourceIds.release()
        val progress = WriteStageProgress(writeProgress)
        val (dexFiles, resFiles) = if (OVERLAP_COMPILE) compileOverlapped(progress) else compileSerially(progress)
        return PatcherResult(dexFiles, resFiles, config.bytecodeMode != BytecodeMode.STRIP_FAST)
    }

    @OptIn(InternalApi::class)
    private fun compileSerially(
        progress: WriteStageProgress
    ): Pair<Set<PatcherResult.PatchedDexFile>, PatcherResult.PatchedResources> {
        val dexFiles = context.bytecodeContext.get()
        context.bytecodeContext.close()
        PhaseProfiler.mark("dex compile")
        progress.finished(WritePhase.DEX_COMPILE)
        val resFiles = context.resourceContext.get()
        PhaseProfiler.mark("resource compile")
        progress.finished(WritePhase.RESOURCE_COMPILE)
        return dexFiles to resFiles
    }

    /**
     * Runs the dex and resource compile on their own threads. They write to disjoint outputs: dex to the
     * `dex*` directories of the patched files, resources to `resources.apk` and `other/`.
     */
    @OptIn(InternalApi::class)
    private fun compileOverlapped(
        progress: WriteStageProgress
    ): Pair<Set<PatcherResult.PatchedDexFile>, PatcherResult.PatchedResources> {
        val compiled = runInParallel(
            "oru-dex-compile",
            {
                val start = PhaseProfiler.nanoStart()
                try {
                    context.bytecodeContext.get().also {
                        context.bytecodeContext.close()
                        progress.finished(WritePhase.DEX_COMPILE)
                    }
                } finally {
                    PhaseProfiler.timeSince("compile.dex", start)
                }
            },
            "oru-resource-compile",
            { context.resourceContext.get().also { progress.finished(WritePhase.RESOURCE_COMPILE) } },
        )
        PhaseProfiler.mark("compile overlapped")
        return compiled
    }
}

/**
 * Set `oru.patcher.overlap` to `true` or `false` to choose; otherwise the dex and resource compile overlap only
 * when the heap limit has room for both (see [overlapFitsHeap]).
 */
private val OVERLAP_COMPILE = System.getProperty("oru.patcher.overlap")?.toBoolean() ?: overlapFitsHeap()
