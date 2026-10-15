/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 *
 * Original forked code:
 * https://github.com/LisoUseInAIKyrios/revanced-patcher
 */

package app.morphe.patcher.util.proxy.mutableTypes

import app.morphe.patcher.util.proxy.mutableTypes.MutableAnnotation.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.base.reference.BaseTypeReference
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.util.FieldUtil
import com.android.tools.smali.dexlib2.util.MethodUtil

class MutableClass(classDef: ClassDef) :
    BaseTypeReference(),
    ClassDef {
    // Class
    private var type = classDef.type
    private var sourceFile = classDef.sourceFile
    private var accessFlags = classDef.accessFlags
    private var superclass = classDef.superclass

    private val _interfaces by lazy { classDef.interfaces.toMutableList() }
    private val _annotations by lazy {
        classDef.annotations.map { annotation -> annotation.toMutable() }.toMutableSet()
    }

    // Methods
    private val methodsDelegate = lazy { classDef.methods.map { method -> method.toMutable() }.toMutableSet() }
    private val _methods by methodsDelegate
    private val _directMethods by lazy { FilteredMethodSet(_methods) { method -> MethodUtil.isDirect(method) } }
    private val _virtualMethods by lazy { FilteredMethodSet(_methods) { method -> !MethodUtil.isDirect(method) } }

    // Fields
    private val _fields by lazy { classDef.fields.map { field -> field.toMutable() }.toMutableSet() }
    private val _staticFields by lazy { _fields.filter { field -> FieldUtil.isStatic(field) }.toMutableSet() }
    private val _instanceFields by lazy { _fields.filter { field -> !FieldUtil.isStatic(field) }.toMutableSet() }

    /**
     * If the mutable methods were created. Until then, the methods
     * are the same as the methods of the class this class was created from.
     */
    internal val areMethodsCreated: Boolean
        get() = methodsDelegate.isInitialized()

    fun setType(type: String) {
        this.type = type
    }

    fun setSourceFile(sourceFile: String?) {
        this.sourceFile = sourceFile
    }

    fun setAccessFlags(accessFlags: Int) {
        this.accessFlags = accessFlags
    }

    fun setSuperClass(superclass: String?) {
        this.superclass = superclass
    }

    override fun getType(): String = type

    override fun getAccessFlags(): Int = accessFlags

    override fun getSourceFile(): String? = sourceFile

    override fun getSuperclass(): String? = superclass

    override fun getInterfaces(): MutableList<String> = _interfaces

    override fun getAnnotations(): MutableSet<MutableAnnotation> = _annotations

    override fun getStaticFields(): MutableSet<MutableField> = _staticFields

    override fun getInstanceFields(): MutableSet<MutableField> = _instanceFields

    override fun getFields(): MutableSet<MutableField> = _fields

    override fun getDirectMethods(): MutableSet<MutableMethod> = _directMethods

    override fun getVirtualMethods(): MutableSet<MutableMethod> = _virtualMethods

    override fun getMethods(): MutableSet<MutableMethod> = _methods

    companion object {
        fun ClassDef.toMutable(): MutableClass = MutableClass(this)
    }
}

/**
 * A live view of the methods of [source] that match [predicate]. Reads and writes go
 * through [source], so the view never disagrees with it. Adding a method that does not
 * match [predicate] is rejected.
 */
private class FilteredMethodSet(
    private val source: MutableSet<MutableMethod>,
    private val predicate: (MutableMethod) -> Boolean,
) : AbstractMutableSet<MutableMethod>() {
    override val size: Int get() = source.count(predicate)

    override fun contains(element: MutableMethod) = predicate(element) && source.contains(element)

    override fun add(element: MutableMethod): Boolean {
        require(predicate(element)) { "Method ${element.name} does not belong in this set" }
        return source.add(element)
    }

    override fun remove(element: MutableMethod) = predicate(element) && source.remove(element)

    override fun iterator(): MutableIterator<MutableMethod> {
        val matches = source.filter(predicate).iterator()
        var last: MutableMethod? = null
        return object : MutableIterator<MutableMethod> {
            override fun hasNext() = matches.hasNext()

            override fun next() = matches.next().also { last = it }

            override fun remove() {
                source.remove(checkNotNull(last))
                last = null
            }
        }
    }
}
