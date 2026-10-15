/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.util

import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import io.mockk.mockk
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test

/**
 * Build time, retained heap and a digest of every lookup answer of the Kotlin class index.
 * Skipped unless `ORU_IDX_BENCH_APK` names an APK. Results are appended to the file named by
 * `ORU_IDX_BENCH_OUT`, if set, with the label `ORU_IDX_BENCH_LABEL`.
 *
 * The digest covers every string, type, member name and literal lookup, with the order of each list,
 * so two code versions with the same digest answer every lookup the same way.
 */
internal class PatchClassesIndexBenchmark {
    @TempDir
    lateinit var temporaryDirectory: File

    @Test
    fun `kotlin class index build time, retained heap and lookup digest`() {
        val apk = System.getenv("ORU_IDX_BENCH_APK")?.let(::File)
        assumeTrue(apk != null && apk.isFile, "ORU_IDX_BENCH_APK is not set")

        val context = BytecodePatchContext(
            PatcherConfig(apk!!, temporaryDirectory.resolve("base")).apply { initializeTemporaryFilesDirectories() },
            mockk(relaxed = true),
        )
        context.decodeDexFiles()
        val classes = LinkedHashSet(context.patchClasses.classMap.values.map { it.classDef })

        val buildMillis = ArrayList<Double>()
        val methodPassMillis = ArrayList<Double>()
        repeat(WARMUP_RUNS + TIMED_RUNS) { run ->
            val patchClasses = PatchClasses(classes)
            val start = System.nanoTime()
            patchClasses.getClassesByReferenceMap()
            val built = System.nanoTime()
            // The first full pass over the methods after the index, which a later scan pays for.
            patchClasses.classMap.values.forEach { wrapper -> check(wrapper.methods.count() >= 0) }
            val passed = System.nanoTime()
            if (run >= WARMUP_RUNS) {
                buildMillis += (built - start) / 1_000_000.0
                methodPassMillis += (passed - built) / 1_000_000.0
            }
        }

        val retainedBytes = (0 until HEAP_RUNS).map { retainedIndexBytes(classes) }.sorted()

        val patchClasses = PatchClasses(classes)
        patchClasses.getClassesByReferenceMap()
        val digest = lookupDigest(patchClasses, classes)

        report(
            "classes: ${classes.size}",
            "kotlin index build ms: ${summary(buildMillis)}",
            "first method pass after index ms: ${summary(methodPassMillis)}",
            "kotlin index retained MB after GC (median of $HEAP_RUNS): ${"%.1f".format(retainedBytes[HEAP_RUNS / 2] / 1e6)}" +
                " (min ${"%.1f".format(retainedBytes.first() / 1e6)}, max ${"%.1f".format(retainedBytes.last() / 1e6)})",
            "lookup digest: $digest",
        )
        context.close()
    }

    /** Live bytes after GC added by building the index of a fresh [PatchClasses]. */
    private fun retainedIndexBytes(classes: Set<ClassDef>): Long {
        val patchClasses = PatchClasses(classes)
        val before = usedAfterGc()
        patchClasses.getClassesByReferenceMap()
        val after = usedAfterGc()
        check(patchClasses.classMap.isNotEmpty())
        return after - before
    }

    private fun usedAfterGc(): Long {
        val runtime = Runtime.getRuntime()
        repeat(GC_PASSES) { System.gc() }
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun lookupDigest(patchClasses: PatchClasses, classes: Set<ClassDef>): String {
        val strings = patchClasses.getClassesByReferenceMap().keys.sorted()
        val types = classes.map { it.type }.distinct().sorted()
        val names = classes.flatMap { c -> c.methods.map { it.name } + c.fields.map { it.name } }.distinct().sorted()
        val literals = classes.flatMap { c ->
            c.methods.flatMap { m -> m.implementation?.instructions?.mapNotNull { (it as? WideLiteralInstruction)?.wideLiteral }.orEmpty() }
        }.distinct().sorted()
        val digest = MessageDigest.getInstance("SHA-256")
        fun put(text: String) = digest.update((text + "\n").toByteArray())
        fun list(wrappers: List<PatchClasses.ClassDefWrapper>?) =
            put(wrappers?.joinToString(",") { "${it.ordinal}:${it.classDef.type}" } ?: "null")

        strings.forEach { put("s:$it"); list(patchClasses.getClassesFromOpcodeStringLiteral(it)) }
        put("all")
        list(patchClasses.getAllClassesWithStrings())
        types.forEach { type ->
            put("t:$type")
            list(patchClasses.getIndexedClassesReferencingType(type))
        }
        names.forEach { name ->
            put("n:$name")
            list(patchClasses.getIndexedClassesReferencingMemberName(name))
        }
        literals.forEach { literal ->
            put("l:$literal")
            list(patchClasses.getIndexedClassesContainingLiteral(literal))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun summary(millis: List<Double>): String {
        val sorted = millis.sorted()
        val median = sorted[sorted.size / 2]
        return "median %.1f min %.1f max %.1f (n=%d)".format(median, sorted.first(), sorted.last(), sorted.size)
    }

    private fun report(vararg lines: String) {
        val text = lines.joinToString("\n", prefix = "[${System.getenv("ORU_IDX_BENCH_LABEL") ?: "run"}]\n", postfix = "\n")
        System.getenv("ORU_IDX_BENCH_OUT")?.let { File(it).appendText(text) }
        println(text)
    }

    private companion object {
        const val WARMUP_RUNS = 2
        const val TIMED_RUNS = 7
        const val HEAP_RUNS = 3
        const val GC_PASSES = 3
    }
}
