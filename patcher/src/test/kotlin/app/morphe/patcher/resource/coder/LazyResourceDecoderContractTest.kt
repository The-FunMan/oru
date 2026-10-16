package app.morphe.patcher.resource.coder

import com.reandroid.apk.ApkModuleXmlDecoder
import com.reandroid.apk.ResFile
import com.reandroid.arsc.chunk.PackageBlock
import com.reandroid.arsc.chunk.TableBlock
import com.reandroid.arsc.chunk.xml.ResXmlDocument
import com.reandroid.arsc.value.Entry
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the private ARSCLib methods that [LazyResourceDecoder] reflects on. An ARSCLib bump that renames or
 * re-types one of them fails here with the method named, instead of silently falling back to eager decoding.
 * When this fails, update [LazyResourceDecoder] and this list together, then bump the pin.
 */
internal class LazyResourceDecoderContractTest {
    private class Contract(val name: String, val types: List<Class<*>>) {
        fun describe() = "$name(${types.joinToString { it.simpleName }})"
    }

    private val contracts = listOf(
        Contract("decodeTableBlock", listOf(File::class.java, TableBlock::class.java)),
        Contract("decodeValues", listOf(File::class.java, TableBlock::class.java)),
        Contract("decodeOverlayable", listOf(File::class.java, TableBlock::class.java)),
        Contract("serializeXml", listOf(PackageBlock::class.java, ResXmlDocument::class.java, File::class.java)),
        Contract("toDecodeResFile", listOf(File::class.java, ResFile::class.java, PackageBlock::class.java)),
        Contract("addDecodedEntry", listOf(Entry::class.java)),
    )

    /** Descriptions of the contracts that [ApkModuleXmlDecoder] no longer declares with the exact parameter types. */
    private fun missing(contracts: List<Contract>): List<String> = contracts.filter { contract ->
        try {
            ApkModuleXmlDecoder::class.java.getDeclaredMethod(contract.name, *contract.types.toTypedArray())
            false
        } catch (_: NoSuchMethodException) {
            true
        }
    }.map { it.describe() }

    @Test
    fun `every private ARSCLib method the lazy decoder reflects on exists with the exact parameter types`() {
        val absent = missing(contracts)
        assertTrue(
            absent.isEmpty(),
            "ARSCLib ApkModuleXmlDecoder no longer declares ${absent.joinToString()}. " +
                "LazyResourceDecoder reflects on these methods; fix it before bumping the ARSCLib pin.",
        )
    }

    @Test
    fun `the contract check names the method that is missing`() {
        val renamed = Contract("decodeValuesRenamed", listOf(File::class.java, TableBlock::class.java))
        val retyped = Contract("decodeValues", listOf(TableBlock::class.java))

        assertEquals(
            listOf("decodeValuesRenamed(File, TableBlock)", "decodeValues(TableBlock)"),
            missing(listOf(renamed, retyped)),
        )
    }

    @Test
    fun `the lazy decoder enables itself only when every contract is present`() {
        assertTrue(missing(contracts).isEmpty())
        assertTrue(LazyResourceDecoder.supported)
    }
}
