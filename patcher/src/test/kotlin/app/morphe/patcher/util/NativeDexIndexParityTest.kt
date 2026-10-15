package app.morphe.patcher.util

import app.morphe.patcher.dex.NativeDexIndex
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.util.zip.ZipFile
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The native index must answer exactly like the Kotlin index of morphe-patcher, in the same order. Runs only when
 * ORU_TEST_APK (an APK) and ORU_NATIVE_LIB (the native library) are set.
 */
internal class NativeDexIndexParityTest {
    private val apk = System.getenv("ORU_TEST_APK")
    private val lib = System.getenv("ORU_NATIVE_LIB")

    private fun types(list: List<PatchClasses.ClassDefWrapper>?) = list?.map { it.classDef.type }

    @Test
    fun `native lookups equal the kotlin index on a real apk`() {
        assumeTrue(apk != null && lib != null, "ORU_TEST_APK and ORU_NATIVE_LIB are not set")
        System.setProperty(NativeDexIndex.LIBRARY_PROPERTY, lib!!)

        val dir = kotlin.io.path.createTempDirectory("native-index-parity").toFile()
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

            // every string literal, with the order of the answer
            val stringMap = kotlinSide.getClassesByReferenceMap()
            stringMap.forEach { (string, list) ->
                assertEquals(types(list), types(nativeSide.getClassesFromOpcodeStringLiteral(string)), "string \"$string\"")
            }
            assertEquals(types(kotlinSide.getAllClassesWithStrings()), types(nativeSide.getAllClassesWithStrings()), "classes with strings")
            assertEquals(null, nativeSide.getClassesFromOpcodeStringLiteral("\u0000no such string\u0000"))
            println("strings compared: ${stringMap.size}")

            // samples of the things the other indexes are asked about
            val random = Random(7)
            val list = classes.toList()
            val sample = List(2500) { list[random.nextInt(list.size)] }
            val sampleTypes = sample.map { it.type }.distinct() + listOf("Ljava/lang/Object;", "Landroid/view/View;", "Lno/Such;")
            val sampleNames = sample.flatMap { c -> c.methods.map { it.name } + c.fields.map { it.name } }.distinct().take(3000) + listOf("<init>", "toString", "noSuchName")
            val sampleLiterals = (sample.take(1500).flatMap { c ->
                c.methods.flatMap { m -> m.implementation?.instructions?.mapNotNull { (it as? WideLiteralInstruction)?.wideLiteral }.orEmpty() }
            }.distinct().take(3000) + listOf(0L, 1L, -1L, 0x7fffffffL, Long.MIN_VALUE, 123456789L))

            fun compare(label: String) {
                sampleTypes.forEach { t ->
                    assertEquals(types(kotlinSide.getIndexedClassesReferencingType(t)), types(nativeSide.getIndexedClassesReferencingType(t)), "$label type $t")
                    assertEquals(types(kotlinSide.getClassesReferencingType(t)), types(nativeSide.getClassesReferencingType(t)), "$label candidates of type $t")
                }
                sampleNames.forEach { n ->
                    assertEquals(types(kotlinSide.getIndexedClassesReferencingMemberName(n)), types(nativeSide.getIndexedClassesReferencingMemberName(n)), "$label member $n")
                }
                sampleLiterals.forEach { l ->
                    assertEquals(types(kotlinSide.getIndexedClassesContainingLiteral(l)), types(nativeSide.getIndexedClassesContainingLiteral(l)), "$label literal $l")
                    assertEquals(types(kotlinSide.getClassesContainingLiteral(l)), types(nativeSide.getClassesContainingLiteral(l)), "$label candidates of literal $l")
                }
            }
            compare("initial")
            println("types ${sampleTypes.size}, member names ${sampleNames.size}, literals ${sampleLiterals.size} compared")

            // classes that become mutable, and some that are replaced the way a merged extension class replaces one
            val allTypes = classes.map { it.type }
            repeat(300) {
                val type = allTypes[random.nextInt(allTypes.size)]
                kotlinSide.mutableClassBy(type)
                nativeSide.mutableClassBy(type)
            }
            repeat(40) {
                val type = allTypes[random.nextInt(allTypes.size)]
                kotlinSide.addClass(kotlinSide.mutableClassBy(type))
                nativeSide.addClass(nativeSide.mutableClassBy(type))
            }
            compare("after changing and replacing classes")
        } finally {
            dir.deleteRecursively()
        }
    }
}
