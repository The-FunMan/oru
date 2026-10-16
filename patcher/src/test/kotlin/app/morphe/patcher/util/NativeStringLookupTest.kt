package app.morphe.patcher.util

import app.morphe.patcher.dex.NativeDexIndex
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.ClassDef
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * String lookups of the native index must equal the Kotlin index, before and after classes are replaced.
 * Runs only when ORU_TEST_APK (an APK) and ORU_NATIVE_LIB (the native library) are set.
 */
internal class NativeStringLookupTest {
    private val apk = System.getenv("ORU_TEST_APK")
    private val lib = System.getenv("ORU_NATIVE_LIB")

    /** Classes that a lookup answers with, replaced classes excluded as candidate lists exclude them. */
    private fun live(list: List<PatchClasses.ClassDefWrapper>?) =
        list.orEmpty().filterNot { it.replaced }.map { it.classDef.type }

    /** Loads the classes of the APK and returns a Kotlin-indexed and a native-indexed [PatchClasses] of them. */
    private fun withSides(block: (kotlinSide: PatchClasses, nativeSide: PatchClasses) -> Unit) {
        assumeTrue(apk != null && lib != null, "ORU_TEST_APK and ORU_NATIVE_LIB are not set")
        System.setProperty(NativeDexIndex.LIBRARY_PROPERTY, lib!!)

        val dir = kotlin.io.path.createTempDirectory("native-string-lookup").toFile()
        try {
            val dexFiles = ZipFile(apk!!).use { zip ->
                zip.entries().asSequence().filter { it.name.startsWith("classes") && it.name.endsWith(".dex") }
                    .sortedBy { it.name }.map { entry ->
                        File(dir, entry.name).also { f -> zip.getInputStream(entry).use { input -> f.outputStream().use(input::copyTo) } }
                    }.toList()
            }
            val classes = LinkedHashSet<ClassDef>()
            dexFiles.forEach { f ->
                val container = DexFileFactory.loadDexContainer(f, Opcodes.getDefault())
                container.dexEntryNames.forEach { classes += container.getEntry(it)!!.dexFile.classes }
            }

            val kotlinSide = PatchClasses(classes)
            val nativeSide = PatchClasses(classes)
            nativeSide.attachNativeIndex(assertNotNull(NativeDexIndex.openOrNull(dexFiles), "native index did not open"))
            block(kotlinSide, nativeSide)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `native exact string lookups equal the kotlin map before and after classes are replaced`() = withSides { kotlinSide, nativeSide ->
        val stringMap = kotlinSide.getClassesByReferenceMap()
        val strings = stringMap.keys.toList()
        // every string, a repeated string and a string that is in no class
        val queries = strings + strings.take(50) + "\u0000no such string\u0000"

        fun compare(label: String) {
            queries.forEach { string ->
                assertEquals(
                    live(kotlinSide.getClassesByReferenceMap()[string]),
                    live(nativeSide.getClassesFromOpcodeStringLiteral(string)),
                    "$label string \"$string\"",
                )
            }
        }
        compare("initial")

        // classes that are replaced by a new wrapper, the way a merged extension class replaces one
        val types = strings.flatMap { stringMap.getValue(it).map { wrapper -> wrapper.classDef.type } }.distinct()
        types.take(40).forEach { type ->
            kotlinSide.addClass(kotlinSide.mutableClassBy(type))
            nativeSide.addClass(nativeSide.mutableClassBy(type))
        }
        compare("after replacing classes")
    }

    @Test
    fun `a class replaced after the kotlin string map was built is in the string map`() = withSides { kotlinSide, nativeSide ->
        val (string, classes) = kotlinSide.getClassesByReferenceMap().entries.first()
        val type = classes.first().classDef.type
        nativeSide.getClassesByReferenceMap() // builds the native side's Kotlin string map first

        kotlinSide.addClass(kotlinSide.mutableClassBy(type))
        nativeSide.addClass(nativeSide.mutableClassBy(type))
        val replacement = nativeSide.classMap.getValue(type)

        assertTrue(
            nativeSide.getClassesByReferenceMap().getValue(string).any { it === replacement },
            "replacement of $type is missing from the string map of \"$string\"",
        )
        assertEquals(
            live(kotlinSide.getClassesByReferenceMap()[string]),
            live(nativeSide.getClassesByReferenceMap()[string]),
            "string map after the replacement",
        )
    }
}
