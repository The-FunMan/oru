/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 *
 * Original forked code:
 * https://github.com/LisoUseInAIKyrios/revanced-patcher
 */

package app.morphe.patcher.util

import app.morphe.patcher.dex.NativeDexIndex
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import com.android.tools.smali.dexlib2.Format
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.ReferenceType
import com.android.tools.smali.dexlib2.dexbacked.DexBackedClassDef
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.dexbacked.DexBackedMethod
import com.android.tools.smali.dexlib2.dexbacked.instruction.DexBackedInstruction
import com.android.tools.smali.dexlib2.dexbacked.raw.FieldIdItem
import com.android.tools.smali.dexlib2.dexbacked.raw.MethodIdItem
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import java.util.stream.Collectors

/**
 * All classes for the target app and any extension classes.
 */
internal class PatchClasses internal constructor(
    /**
     * Class type -> ClassDef.
     */
    internal val classMap: MutableMap<String, ClassDefWrapper>
) {

    /**
     * Container to hold the class definition that is either mutable or immutable.
     *
     * This intermediate container is needed to easily update the class in both
     * the class map and in the string map with a single constant time operation.
     */
    internal class ClassDefWrapper(
        /**
         * Can be immutable or mutable.
         */
        var classDef: ClassDef,
        internal var onMakeMutable: ((ClassDefWrapper) -> Unit)? = null,
    ) {
        /**
         * Position of this class in the class map. Used to keep candidate classes in class map order.
         */
        internal var ordinal = 0

        /**
         * If this class was replaced in the class map by another class of the same type.
         */
        internal var replaced = false

        /**
         * If the class was in the input, or replaced a class that was. Kept when the class is replaced.
         */
        internal var inputClass = false

        /**
         * The immutable class the instruction indexes were built from,
         * or null if the class was not indexed or was mutable when it was indexed.
         */
        internal var indexedClassDef: ClassDef? = null

        /**
         * Methods of [classDef] while it is immutable.
         *
         * Each iteration of dex backed methods decodes the full signature of every method
         * to filter duplicate methods, so the methods are kept after the first iteration.
         */
        private var immutableMethods: List<Method>? = null

        /**
         * The methods of [classDef].
         */
        val methods: Iterable<Method>
            get() {
                val classDefLocal = classDef
                if (classDefLocal is MutableClass) return classDefLocal.methods

                immutableMethods?.let { return it }
                val start = PhaseProfiler.nanoStart()
                val methods = classDefLocal.methods.toList()
                PhaseProfiler.timeSince("classdef.methods.firstTouch", start)
                immutableMethods = methods
                return methods
            }

        /**
         * The methods of [classDef] that can have other instructions than [indexedClassDef],
         * such as added methods and methods with a mutable implementation.
         */
        internal fun changedMethods(): List<Method> {
            val classDefLocal = classDef
            if (classDefLocal !is MutableClass) {
                return if (classDefLocal === indexedClassDef) emptyList() else methods.toList()
            }

            val indexedClassDefLocal = indexedClassDef
            if (indexedClassDefLocal !is DexBackedClassDef) return classDefLocal.methods.toList()
            if (!classDefLocal.areMethodsCreated) return emptyList()

            return classDefLocal.methods.filter { method ->
                method.isImplementationCreated ||
                        (method.sourceMethod as? DexBackedMethod)?.classDef !== indexedClassDefLocal
            }
        }

        fun getMutableClass(): MutableClass {
            if (classDef !is MutableClass) {
                PhaseProfiler.count("classes.madeMutable")
                classDef = MutableClass(classDef)
                immutableMethods = null
                onMakeMutable?.invoke(this)
            }
            return classDef as MutableClass
        }
    }

    private var nextOrdinal = 0

    private val mutableWrappers = LinkedHashSet<ClassDefWrapper>()

    private val onWrapperMadeMutable: (ClassDefWrapper) -> Unit = { wrapper ->
        mutableWrappers.add(wrapper)
    }

    init {
        classMap.values.forEach { wrapper ->
            wrapper.onMakeMutable = onWrapperMadeMutable
            wrapper.ordinal = nextOrdinal++
            if (wrapper.classDef is MutableClass) {
                mutableWrappers.add(wrapper)
            }
        }
    }

    /**
     * Reads the references of instructions of a dex file using the indexes of the references,
     * without creating reference objects. A [app.morphe.patcher.dex.CachingDexBackedDexFile]
     * decodes each string only once.
     */
    private class DexReferenceDecoder(val dexFile: DexBackedDexFile) {
        private val buffer = dexFile.buffer
        val dataBuffer = dexFile.dataBuffer
        private val stringSection = dexFile.stringSection
        private val typeSection = dexFile.typeSection
        private val fieldSection = dexFile.fieldSection
        private val methodSection = dexFile.methodSection

        fun string(stringIndex: Int): String = stringSection[stringIndex]

        fun type(typeIndex: Int) = string(buffer.readSmallUint(typeSection.getOffset(typeIndex)))

        fun fieldDefiningClass(fieldIndex: Int) =
            type(buffer.readUshort(fieldSection.getOffset(fieldIndex) + FieldIdItem.CLASS_OFFSET))

        fun fieldName(fieldIndex: Int) =
            string(buffer.readSmallUint(fieldSection.getOffset(fieldIndex) + FieldIdItem.NAME_OFFSET))

        fun methodDefiningClass(methodIndex: Int) =
            type(buffer.readUshort(methodSection.getOffset(methodIndex) + MethodIdItem.CLASS_OFFSET))

        fun methodName(methodIndex: Int) =
            string(buffer.readSmallUint(methodSection.getOffset(methodIndex) + MethodIdItem.NAME_OFFSET))
    }

    /**
     * Classes by the hashes of indexed values. Unlike a [HashMap], the hashes are not boxed.
     * Different values can have the same hash, so the classes of a hash can be false positives.
     */
    private class HashIndex {
        private var hashes = IntArray(INITIAL_HASH_INDEX_CAPACITY)

        /**
         * Per slot, the class of the hash if it has one class, otherwise the [MutableList] of its classes.
         * Most hashes have one class, so they are stored without a list.
         */
        private var classes = arrayOfNulls<Any>(INITIAL_HASH_INDEX_CAPACITY)
        private var size = 0

        private fun slotOf(hash: Int): Int {
            val mask = hashes.size - 1
            var slot = (hash * -0x61c88647).let { it xor (it ushr 16) } and mask
            while (true) {
                val slotClasses = classes[slot]
                if (slotClasses == null || hashes[slot] == hash) return slot
                slot = (slot + 1) and mask
            }
        }

        @Suppress("UNCHECKED_CAST")
        operator fun get(hash: Int): List<ClassDefWrapper>? = when (val slotClasses = classes[slotOf(hash)]) {
            null -> null
            is ClassDefWrapper -> listOf(slotClasses)
            else -> slotClasses as List<ClassDefWrapper>
        }

        /**
         * Adds [wrapper] to the classes of each hash.
         *
         * @param sortedHashes The hashes of the class, in order. Can have duplicates.
         */
        fun addAll(sortedHashes: IntArray, wrapper: ClassDefWrapper) {
            for (i in sortedHashes.indices) {
                val hash = sortedHashes[i]
                if (i == 0 || sortedHashes[i - 1] != hash) add(hash, wrapper)
            }
        }

        /**
         * Adds [wrapper] to the classes of [hash], unless it was already added.
         *
         * Classes are indexed one at a time, so a class that already holds the hash
         * is always the last class of the list.
         */
        @Suppress("UNCHECKED_CAST")
        private fun add(hash: Int, wrapper: ClassDefWrapper) {
            val slot = slotOf(hash)
            when (val slotClasses = classes[slot]) {
                null -> {
                    hashes[slot] = hash
                    classes[slot] = wrapper
                    // Keep at most half of the slots in use.
                    if (++size * 2 > hashes.size) grow()
                }
                is ClassDefWrapper -> if (slotClasses !== wrapper) classes[slot] = arrayListOf(slotClasses, wrapper)
                else -> {
                    val list = slotClasses as MutableList<ClassDefWrapper>
                    if (list[list.lastIndex] !== wrapper) list += wrapper
                }
            }
        }

        private fun grow() {
            val oldHashes = hashes
            val oldClasses = classes
            hashes = IntArray(oldHashes.size * 2)
            classes = arrayOfNulls(oldHashes.size * 2)
            for (i in oldHashes.indices) {
                val oldSlotClasses = oldClasses[i] ?: continue
                val slot = slotOf(oldHashes[i])
                hashes[slot] = oldHashes[i]
                classes[slot] = oldSlotClasses
            }
        }
    }

    /**
     * Growable list of ints, to collect hashes without boxing them.
     */
    private class IntList {
        private var values = IntArray(16)
        private var size = 0

        fun add(value: Int) {
            if (size == values.size) values = values.copyOf(size * 2)
            values[size++] = value
        }

        fun toSortedArray() = values.copyOf(size).apply { sort() }
    }

    /**
     * The values of the instructions of a class to index.
     *
     * @param classDef The class the values were collected from.
     * @param strings String constants, can have duplicates.
     * @param typeHashes Sorted hashes of referenced types, can have duplicates.
     * @param memberNameHashes Sorted hashes of referenced field and method names, can have duplicates.
     * @param literalHashes Sorted hashes of literals, can have duplicates.
     */
    private class ClassIndexValues(
        val classDef: ClassDef,
        val strings: List<String>,
        val typeHashes: IntArray,
        val memberNameHashes: IntArray,
        val literalHashes: IntArray,
    )

    /**
     * Collects the values of the instructions of a class to index.
     */
    private class ClassIndexCollector {
        val strings = ArrayList<String>()
        val typeHashes = IntList()
        val memberNameHashes = IntList()
        val literalHashes = IntList()

        /**
         * Collects the reference of a dex backed instruction without creating the reference.
         *
         * @return False if the format of the instruction is not supported.
         */
        fun addDexBackedReference(instruction: DexBackedInstruction, decoder: DexReferenceDecoder): Boolean {
            val opcode = instruction.opcode
            val referenceType = opcode.referenceType
            if (referenceType == ReferenceType.NONE) return true

            // All reference formats store the reference index after the opcode.
            val referenceIndexOffset = instruction.instructionStart + 2
            val referenceIndex = when (opcode.format) {
                Format.Format21c, Format.Format22c, Format.Format35c, Format.Format3rc,
                Format.Format45cc, Format.Format4rcc -> decoder.dataBuffer.readUshort(referenceIndexOffset)
                Format.Format31c -> decoder.dataBuffer.readSmallUint(referenceIndexOffset)
                else -> return false
            }

            when (referenceType) {
                ReferenceType.STRING -> if (opcode == Opcode.CONST_STRING || opcode == Opcode.CONST_STRING_JUMBO) {
                    strings += decoder.string(referenceIndex)
                }
                ReferenceType.TYPE -> typeHashes.add(decoder.type(referenceIndex).hashCode())
                ReferenceType.FIELD -> {
                    typeHashes.add(decoder.fieldDefiningClass(referenceIndex).hashCode())
                    memberNameHashes.add(decoder.fieldName(referenceIndex).hashCode())
                }
                ReferenceType.METHOD -> {
                    typeHashes.add(decoder.methodDefiningClass(referenceIndex).hashCode())
                    memberNameHashes.add(decoder.methodName(referenceIndex).hashCode())
                }
            }
            return true
        }

        /**
         * Collects the reference of any instruction.
         */
        fun addReference(instruction: Instruction) {
            when (val reference = (instruction as? ReferenceInstruction)?.reference ?: return) {
                is StringReference -> if (
                    instruction.opcode == Opcode.CONST_STRING ||
                    instruction.opcode == Opcode.CONST_STRING_JUMBO
                ) {
                    strings += reference.string
                }
                is MethodReference -> {
                    typeHashes.add(reference.definingClass.hashCode())
                    memberNameHashes.add(reference.name.hashCode())
                }
                is FieldReference -> {
                    typeHashes.add(reference.definingClass.hashCode())
                    memberNameHashes.add(reference.name.hashCode())
                }
                is TypeReference -> typeHashes.add(reference.type.hashCode())
            }
        }
    }

    /**
     * Collects the string, type-reference, member-name-reference,
     * and literal values of [ClassDefWrapper.classDef] in one traversal.
     *
     * Only reads this class, so classes can be collected in parallel.
     */
    private fun ClassDefWrapper.collectIndexValues(): ClassIndexValues {
        val classDef = classDef
        val decoder = (classDef as? DexBackedClassDef)?.let { DexReferenceDecoder(it.dexFile) }
        val collector = ClassIndexCollector()

        // Read the class directly, so the index does not keep the method list of every class for the whole run.
        classDef.methods.forEach { method ->
            method.instructionsOrNull?.forEach { instruction ->
                if (instruction is WideLiteralInstruction) {
                    collector.literalHashes.add(instruction.wideLiteral.hashCode())
                }

                if (decoder == null || instruction !is DexBackedInstruction ||
                    instruction.dexFile !== decoder.dexFile ||
                    !collector.addDexBackedReference(instruction, decoder)
                ) {
                    collector.addReference(instruction)
                }
            }
        }

        return ClassIndexValues(
            classDef,
            collector.strings,
            collector.typeHashes.toSortedArray(),
            collector.memberNameHashes.toSortedArray(),
            collector.literalHashes.toSortedArray(),
        )
    }

    /**
     * The indexes of the instruction values.
     */
    private class IndexBuilder {
        // Default 0.75f load factor works well and a lower value does not improve patching time.
        val strings = HashMap<String, MutableList<ClassDefWrapper>>()
        val types = HashIndex()
        val memberNames = HashIndex()
        val literals = HashIndex()
        val classesWithStrings = ArrayList<ClassDefWrapper>()

        /**
         * Adds the values of a class to the indexes. Classes are added in class map order.
         */
        fun add(wrapper: ClassDefWrapper, values: ClassIndexValues) {
            wrapper.indexedClassDef = values.classDef.takeUnless { it is MutableClass }

            if (values.strings.isNotEmpty()) {
                values.strings.forEach { string ->
                    val list = strings.getOrPut(string) { ArrayList(1) }
                    // A class that already holds the string is always the last class of the list.
                    if (list.isEmpty() || list[list.lastIndex] !== wrapper) list += wrapper
                }
                classesWithStrings += wrapper
            }
            types.addAll(values.typeHashes, wrapper)
            memberNames.addAll(values.memberNameHashes, wrapper)
            literals.addAll(values.literalHashes, wrapper)
        }
    }

    /**
     * Opcode string constant -> List<ClassDefWrapper>
     */
    private var stringMap: MutableMap<String, MutableList<ClassDefWrapper>>? = null

    /**
     * Instructions indexes, or null if not yet built.
     */
    private var indexBuilder: IndexBuilder? = null

    /**
     * All classes that contain at least 1 string.
     * Same contents as [stringMap] values except contains no duplicates.
     */
    private var allClassesWithStrings: MutableList<ClassDefWrapper>? = null

    /**
     * Indexes of the classes of the input APK, computed natively. Null when the native library is not available,
     * and then every class is indexed in Kotlin. With it, [indexBuilder] only holds the classes it does not know:
     * those added or changed since the input was read.
     */
    private var nativeIndex: NativeDexIndex? = null

    /** The class at each native ordinal, or null where the native answer is not used for it. */
    private var nativeWrappers: Array<ClassDefWrapper?> = emptyArray()

    /**
     * The Kotlin index of the strings of all classes, built only when a search cannot be answered by exact string.
     * [addClass] keeps it up to date.
     */
    private var kotlinStringIndex: IndexBuilder? = null

    /** Answers lookups for the classes of the input APK from [index] instead of indexing them in Kotlin. */
    internal fun attachNativeIndex(index: NativeDexIndex) {
        nativeIndex = index
    }

    private fun IntArray.toWrappers(): MutableList<ClassDefWrapper> {
        val out = ArrayList<ClassDefWrapper>(size)
        for (ordinal in this) nativeWrappers[ordinal]?.let(out::add)
        return out
    }

    /** Classes added after the index was built, which the Kotlin index lists after all others. */
    private val lateWrappers = java.util.IdentityHashMap<ClassDefWrapper, Boolean>()

    /** The native answer and what the Kotlin overlay knows, in the order the Kotlin index lists them. */
    private fun MutableList<ClassDefWrapper>.withOverlay(overlay: List<ClassDefWrapper>?): List<ClassDefWrapper> {
        if (!overlay.isNullOrEmpty()) addAll(overlay)
        if (size > 1) sortWith(compareBy({ if (lateWrappers.containsKey(it)) 1 else 0 }, { if (lateWrappers.containsKey(it)) 0 else it.ordinal }))
        return this
    }

    private fun buildWithNative(index: NativeDexIndex): IndexBuilder {
        nativeWrappers = Array(index.descriptors.size) { classMap[index.descriptors[it]] }
        val covered = java.util.IdentityHashMap<ClassDefWrapper, Boolean>()
        for (i in nativeWrappers.indices) {
            val wrapper = nativeWrappers[i]
            // a class that is changed or replaced already is indexed from its current instructions instead
            if (wrapper == null || wrapper.classDef is MutableClass || wrapper.replaced) nativeWrappers[i] = null
            else covered[wrapper] = true
        }
        val builder = IndexBuilder()
        val others = ArrayList<ClassDefWrapper>()
        classMap.values.forEach { wrapper ->
            if (covered.containsKey(wrapper)) wrapper.indexedClassDef = wrapper.classDef else others += wrapper
        }
        others.chunked(INDEX_CHUNK_SIZE).forEach { chunk ->
            chunk.parallelStream().map { wrapper -> wrapper.collectIndexValues() }.collect(Collectors.toList())
                .forEachIndexed { i, values -> builder.add(chunk[i], values) }
        }
        indexBuilder = builder
        return builder
    }

    private fun fullKotlinStringMap(): Map<String, List<ClassDefWrapper>> = (kotlinStringIndex ?: run {
        val builder = IndexBuilder()
        classMap.values.chunked(INDEX_CHUNK_SIZE).forEach { chunk ->
            chunk.parallelStream().map { wrapper -> wrapper.collectIndexValues() }.collect(Collectors.toList())
                .forEachIndexed { i, values -> builder.add(chunk[i], values) }
        }
        builder.also { kotlinStringIndex = it }
    }).strings

    internal constructor(set: Set<ClassDef>) : this(
        // Must use linked hash map. A regular map does not preserve the order of classes found
        // in the apk, so old fingerprints that have multiple matches can match the wrong class
        // due to hashmap random class iteration during matching. The issue is with
        // some fingerprint declarations not being unique enough and currently there is no way to
        // check for duplicate matches.
        // See https://github.com/ReVanced/revanced-patcher/issues/74
        //
        // Pre-size so rehashing doesn't occur and use a more performant load factor.
        LinkedHashMap<String, ClassDefWrapper>(2 * set.size, 0.5f)
    ) {
        for (classDef in set) {
            val wrapper = ClassDefWrapper(classDef, onWrapperMadeMutable)
            wrapper.ordinal = nextOrdinal++
            wrapper.inputClass = true
            if (classDef is MutableClass) {
                mutableWrappers.add(wrapper)
            }
            classMap[classDef.type] = wrapper
        }
    }

    internal fun close() {
        classMap.clear()
        closeReferenceMap()
        nativeIndex?.close()
        nativeIndex = null
    }

    internal fun closeReferenceMap() {
        stringMap = null
        indexBuilder = null
        allClassesWithStrings = null
        nativeWrappers = emptyArray()
        kotlinStringIndex = null
        lateWrappers.clear()
        mutableWrappers.clear()
        classMap.values.forEach { wrapper ->
            if (wrapper.classDef is MutableClass) {
                mutableWrappers.add(wrapper)
            }
        }
    }

    internal fun addClass(classDef: ClassDef) {
        PhaseProfiler.count("classes.added")
        val wrapper = ClassDefWrapper(classDef, onWrapperMadeMutable)
        if (classDef is MutableClass) {
            mutableWrappers.add(wrapper)
        }

        val replacedWrapper = classMap.put(classDef.type, wrapper)
        if (replacedWrapper == null) {
            wrapper.ordinal = nextOrdinal++
        } else {
            // The class keeps its position in the class map.
            wrapper.ordinal = replacedWrapper.ordinal
            wrapper.inputClass = replacedWrapper.inputClass
            replacedWrapper.replaced = true
            mutableWrappers.remove(replacedWrapper)
        }

        // Classes are added while patches execute (extension merges), which can happen after the
        // instruction indexes were built. Index the new class incrementally, otherwise string
        // lookups and candidate scans never see it.
        if (nativeIndex != null && indexBuilder != null) lateWrappers[wrapper] = true
        val stringIndex = kotlinStringIndex
        if (indexBuilder != null || stringIndex != null) {
            val values = wrapper.collectIndexValues()
            indexBuilder?.add(wrapper, values)
            stringIndex?.add(wrapper, values)
        }
    }

    internal fun getClassesByReferenceMap(): Map<String, List<ClassDefWrapper>> {
        if (nativeIndex != null) return fullKotlinStringMap()
        if (stringMap != null) {
            return stringMap!!
        }

        return buildInstructionIndexes()
    }

    private fun buildInstructionIndexes(): Map<String, List<ClassDefWrapper>> {
        val builder = IndexBuilder()

        // Scanning the instructions is the costly part and reads each class on its own, so it runs
        // in parallel, a chunk at a time to bound memory, while the indexes are filled in class order.
        classMap.values.chunked(INDEX_CHUNK_SIZE).forEach { chunk ->
            chunk.parallelStream().map { wrapper -> wrapper.collectIndexValues() }.collect(Collectors.toList())
                .forEachIndexed { i, values -> builder.add(chunk[i], values) }
        }

        stringMap = builder.strings
        indexBuilder = builder
        allClassesWithStrings = builder.classesWithStrings
        return builder.strings
    }

    private fun getIndexBuilder(): IndexBuilder {
        nativeIndex?.let { return indexBuilder ?: buildWithNative(it) }
        getClassesByReferenceMap() // Load reference map if needed.
        return indexBuilder!!
    }

    internal fun getClassesFromOpcodeStringLiteral(stringLiteral: String): List<ClassDefWrapper>? {
        val native = nativeIndex ?: return getClassesByReferenceMap()[stringLiteral]
        val overlay = getIndexBuilder().strings[stringLiteral]
        return native.withString(stringLiteral).toWrappers().withOverlay(overlay).ifEmpty { null }
    }

    internal fun getAllClassesWithStrings(): List<ClassDefWrapper> {
        val native = nativeIndex
        if (native != null) {
            return native.withAnyString().toWrappers().withOverlay(getIndexBuilder().classesWithStrings)
        }
        getClassesByReferenceMap() // Load string map if needed.
        return allClassesWithStrings!!
    }

    /**
     * Indexed classes with instructions that reference the type. Can contain false positives.
     * Does not include changes of mutable classes, see [candidateClasses].
     */
    internal fun getIndexedClassesReferencingType(type: String): List<ClassDefWrapper> {
        val builder = getIndexBuilder()
        val native = nativeIndex ?: return builder.types[type.hashCode()].orEmpty()
        return native.withTypeHash(type.hashCode()).toWrappers().withOverlay(builder.types[type.hashCode()])
    }

    /**
     * Indexed classes with instructions that reference a field or method with the name.
     * Can contain false positives. Does not include changes of mutable classes, see [candidateClasses].
     */
    internal fun getIndexedClassesReferencingMemberName(name: String): List<ClassDefWrapper> {
        val builder = getIndexBuilder()
        val native = nativeIndex ?: return builder.memberNames[name.hashCode()].orEmpty()
        return native.withMemberHash(name.hashCode()).toWrappers().withOverlay(builder.memberNames[name.hashCode()])
    }

    /**
     * Indexed classes with instructions that use the literal.
     * Does not include changes of mutable classes, see [candidateClasses].
     */
    internal fun getIndexedClassesContainingLiteral(literal: Long): List<ClassDefWrapper> {
        val builder = getIndexBuilder()
        val native = nativeIndex ?: return builder.literals[literal.hashCode()].orEmpty()
        return native.withLiteralHash(literal.hashCode()).toWrappers().withOverlay(builder.literals[literal.hashCode()])
    }

    /**
     * Classes that can satisfy conditions of the indexed instruction values.
     *
     * @param classes The candidate classes in class map order.
     * @param changedMethods Changed methods of candidate mutable classes that cannot satisfy the
     *                       conditions with the indexed instructions. Only the changed methods of
     *                       these classes can satisfy the conditions.
     */
    internal class Candidates(
        val classes: List<ClassDefWrapper>,
        private val changedMethods: Map<ClassDefWrapper, List<Method>>,
    ) {
        private val classSet by lazy { classes.toHashSet() }

        operator fun contains(wrapper: ClassDefWrapper) = classSet.contains(wrapper)

        /**
         * The methods of a candidate class that can satisfy the conditions.
         */
        fun methodsOf(wrapper: ClassDefWrapper): Iterable<Method> = changedMethods[wrapper] ?: wrapper.methods
    }

    /**
     * Classes that can satisfy all the conditions, in class map order.
     *
     * @param indexedClasses Indexed classes of each condition.
     *                       A class must be in all lists to satisfy all conditions.
     * @return The indexed classes in all lists, and the mutable classes with
     *         changed methods because their contents can differ from the indexes.
     */
    internal fun candidateClasses(indexedClasses: List<List<ClassDefWrapper>>): Candidates {
        val smallest = indexedClasses.minBy { it.size }
        val result = ArrayList<ClassDefWrapper>(smallest.size + mutableWrappers.size)

        if (smallest.isNotEmpty()) {
            // Classes of a much larger list are left for the matching to reject,
            // as finding them in a large list costs more than the matching.
            val maximumSize = CANDIDATE_INTERSECTION_SIZE_FACTOR * smallest.size
            val others = indexedClasses.mapNotNull { classes ->
                if (classes === smallest || classes.size > maximumSize) null else HashSet(classes)
            }

            smallest.filterTo(result) { wrapper ->
                !wrapper.replaced && others.all { it.contains(wrapper) }
            }
        }

        var changedMethods = emptyMap<ClassDefWrapper, List<Method>>()
        if (mutableWrappers.isNotEmpty()) {
            val indexed = HashSet(result)
            changedMethods = HashMap()
            mutableWrappers.forEach { wrapper ->
                if (indexed.contains(wrapper)) return@forEach

                val methods = wrapper.changedMethods()
                if (methods.isNotEmpty()) {
                    result += wrapper
                    changedMethods[wrapper] = methods
                }
            }
        }

        // Lists are in class map order, except for replaced classes and mutable classes.
        for (i in 1 until result.size) {
            if (result[i - 1].ordinal > result[i].ordinal) {
                result.sortBy { it.ordinal }
                break
            }
        }

        return Candidates(result, changedMethods)
    }

    internal fun getClassesReferencingType(type: String): List<ClassDefWrapper>? =
        candidateClasses(listOf(getIndexedClassesReferencingType(type))).classes.ifEmpty { null }

    internal fun getClassesContainingLiteral(literal: Long): List<ClassDefWrapper>? =
        candidateClasses(listOf(getIndexedClassesContainingLiteral(literal))).classes.ifEmpty { null }

    private companion object {
        /**
         * Lists of candidates larger than this factor of the smallest list are not intersected.
         */
        private const val CANDIDATE_INTERSECTION_SIZE_FACTOR = 16

        /**
         * Must be a power of 2.
         */
        private const val INITIAL_HASH_INDEX_CAPACITY = 1 shl 16

        private const val INDEX_CHUNK_SIZE = 4096
    }

    /**
     * Iterate over all classes.
     */
    fun forEach(action: (ClassDef) -> Unit) {
        classMap.values.forEach { wrapper ->
            action(wrapper.classDef)
        }
    }

    /**
     * Find a class with a predicate.
     *
     * @param classType The full classname.
     * @return An immutable instance of the class type.
     * @see mutableClassBy
     */
    fun classByOrNull(classType: String) = classMap[classType]?.classDef

    private fun mapWrapperByOrNull(predicate: (ClassDef) -> Boolean) =
        classMap.values.find { wrapper ->
            predicate(wrapper.classDef)
        }

    /**
     * Find a class with a predicate. If you know the class type name,
     * it is highly preferred to instead use [classByOrNull(String)].
     *
     * @param predicate A predicate to match the class.
     * @return An immutable instance of the class type, or null if not found.
     */
    fun classByOrNull(predicate: (ClassDef) -> Boolean) = mapWrapperByOrNull(predicate)?.classDef

    /**
     * Find a class with a predicate.
     *
     * @param predicate A predicate to match the class.
     * @return An immutable instance of the class type.
     */
    fun classBy(predicate: (ClassDef) -> Boolean) = classByOrNull(predicate)
        ?: throw PatchException("Could not find any class match")

    /**
     * Find a class with a predicate.
     *
     * @param classType The full classname.
     * @return An immutable instance of the class type.
     * @see mutableClassBy
     */
    fun classBy(classType: String) = classByOrNull(classType)
        ?: throw PatchException("Could not find class: $classType")

    /**
     * Mutable class from a full class name.
     * Returns `null` if class is not available, such as a built-in Android or Java library.
     *
     * @param classDefType The full classname.
     * @return A mutable version of the class type.
     */
    fun mutableClassByOrNull(classDefType: String): MutableClass? {
        val wrapper = classMap[classDefType] ?: return null
        return wrapper.getMutableClass()
    }

    /**
     * Find a class with a predicate.
     *
     * @param classDefType The full classname.
     * @return A mutable version of the class type.
     */
    fun mutableClassBy(classDefType: String) = mutableClassByOrNull(classDefType)
        ?: throw PatchException("Could not find class: $classDefType")

    /**
     * Find a mutable class with a predicate.
     *
     * @param predicate A predicate to match the class.
     * @return A mutable class that matches the predicate.
     */
    fun mutableClassByOrNull(predicate: (ClassDef) -> Boolean) =
        mapWrapperByOrNull(predicate)?.getMutableClass()

    /**
     * @param classDef An immutable class.
     * @return A mutable version of the class definition.
     */
    fun mutableClassBy(classDef: ClassDef): MutableClass =
        classDef as? MutableClass ?: mutableClassBy(classDef.type)

    /**
     * Find a mutable class with a predicate.
     *
     * @param predicate A predicate to match the class.
     * @return A mutable class that matches the predicate.
     */
    fun mutableClassBy(predicate: (ClassDef) -> Boolean) = mutableClassByOrNull(predicate)
        ?: throw PatchException("Could not find any class match")
}
