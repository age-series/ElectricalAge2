package org.eln2.mc.utility

import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

fun noop() { }

/**
 * Scans the target class [TInst] for fields annotated with [TFldAnnotation], and creates a list of [FieldReader]s, caching the result in [target].
 * The field types must be subclasses of [superK]
 * */
fun<TFldAnnotation : Annotation, TInst : Any> fieldScan(
    inst: Class<TInst>,
    superK: KClass<*>,
    annotC: Class<TFldAnnotation>,
    target: ConcurrentHashMap<Class<*>, List<FieldReader<TInst>>>
): List<FieldReader<TInst>> {
    return target.getOrPut(inst) {
        val accessors = mutableListOf<FieldReader<TInst>>()

        inst.kotlin
            .memberProperties
            .filter { it.javaField?.isAnnotationPresent(annotC) ?: false }
            .forEach {
                if(!(it.returnType.classifier as KClass<*>).isSubclassOf(superK)) {
                    error("Invalid $superK field $it")
                }

                accessors.add(it::get)
            }

        accessors
    }
}

fun interface FieldReader<TInst: Any> {
    fun get(inst: TInst): Any?
}

private val classId = HashMap<KClass<*>, Int>()

val KClass<*>.reflectId: Int get() = synchronized(classId) {
    classId.getOrPut(this) {
        val result = (this.qualifiedName ?: error("Failed to get name of $this")).hashCode()

        if(classId.values.any { it == result}) {
            error("reflect ID collision $this")
        }

        result
    }
}
