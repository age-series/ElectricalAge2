package org.eln2.mc

import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

fun noop(){}

/**
 * Scans the target class [I] for fields annotated with [FA], and creates a list of [FieldReader]s, caching the result in [target].
 * The field types must be subclasses of [superK]
 * */
fun <FA : Annotation, I : Any> fieldScan(
    inst: Class<I>,
    superK: KClass<*>,
    annotC: Class<FA>,
    target: ConcurrentHashMap<Class<*>, List<FieldInfo<I>>>
): List<FieldInfo<I>> {
    return target.getOrPut(inst) {
        val accessors = mutableListOf<FieldInfo<I>>()

        inst.kotlin
            .memberProperties
            .filter { it.javaField?.isAnnotationPresent(annotC) ?: false }
            .forEach { property ->
                if (!(property.returnType.classifier as KClass<*>).isSubclassOf(superK)) {
                    error("Invalid $superK field $property")
                }

                val getProperty = property::get

                accessors.add(FieldInfo(property.javaField!!) {
                    getProperty(it)
                })
            }

        accessors
    }
}

data class FieldInfo<I : Any>(
    val field: Field,
    val reader: FieldReader<I>
)

fun interface FieldReader<I : Any> {
    fun get(inst: I): Any?
}

private val classId = HashMap<KClass<*>, Int>()

val KClass<*>.reflectId: Int
    get() = synchronized(classId) {
        classId.getOrPut(this) {
            val result = (this.qualifiedName ?: error("Failed to get name of $this")).hashCode()

            if (classId.values.any { it == result }) {
                error("reflect ID collision $this")
            }

            result
        }
    }

fun interface ServiceProvider<T> {
    fun getInstance(): T
}

fun interface ExternalResolver {
    fun resolve(c: Class<*>): Any?
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CONSTRUCTOR)
annotation class Inj

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class NoInj

class ServiceCollection {
    private val services = HashMap<Class<*>, ServiceProvider<*>>()
    private val externalResolvers = ArrayList<ExternalResolver>()

    fun <T> withService(c: Class<T>, provider: ServiceProvider<T>): ServiceCollection {
        if (services.put(c, provider) != null) {
            error("Duplicate service $c")
        }

        return this
    }

    inline fun <reified T> withService(provider: ServiceProvider<T>) = withService(T::class.java, provider)

    fun withExternalResolver(r: ExternalResolver): ServiceCollection {
        externalResolvers.add(r)
        return this
    }

    fun resolve(c: Class<*>): Any? {
        var instance = services[c]?.getInstance()

        if (instance != null) {
            return instance
        }

        externalResolvers.forEach {
            instance = it.resolve(c)

            if (instance != null) {
                return instance
            }
        }

        return null
    }

    fun activate(c: Class<*>, extraParams: List<Any>): Any {
        require(!c.isAnnotationPresent(NoInj::class.java)) {
            "Class $c is marked as no-inject!"
        }

        // If @Inj is used on any of the constructors, only constructors annotated with @Inj are used. Otherwise, all constructors are used.
        val activators = activators.getOrPut(c) {
            val constructors = if (c.constructors.any { it.getAnnotation(Inj::class.java) != null }) {
                c.constructors.filter { it.getAnnotation(Inj::class.java) != null }
            } else {
                c.constructors.toList()
            }

            constructors.map { ctor ->
                val args = ArrayList<Class<*>>()

                ctor.parameters.forEach {
                    val pClass = it.parameterizedType as Class<*>

                    if (args.contains(pClass)) {
                        error("Ambiguous inject parameter $it")
                    }

                    args.add(pClass)
                }

                ActivatorRsi(args.toTypedArray()) { inst -> ctor.newInstance(*inst) }
            }.toTypedArray()
        }

        activators.forEach { rsi ->
            val args = Array<Any?>(rsi.parameters.size) { null }

            rsi.parameters.forEachIndexed { index, paramClass ->
                args[index] = resolve(paramClass)
                    ?: extraParams.firstOrNull {
                        paramClass.isAssignableFrom(it.javaClass)
                    } ?: return@forEach
            }

            return rsi.activator(args)
        }

        error("Failed to solve constructor for $c")
    }

    inline fun <reified T> activate(extraParams: List<Any>): T = activate(T::class.java, extraParams) as T

    private class ActivatorRsi(
        val parameters: Array<Class<*>>,
        val activator: (Array<Any?>) -> Any,
    )

    companion object {
        private val activators = ConcurrentHashMap<Class<*>, Array<ActivatorRsi>>()
    }
}

inline fun <reified T> ServiceCollection.withSingleton(noinline resolver: () -> T): ServiceCollection {
    val lazy = lazy(resolver)
    return this.withService { lazy.value }
}

fun <T> ServiceCollection.withSingleton(c: Class<T>, resolver: () -> T): ServiceCollection {
    val lazy = lazy(resolver)
    return this.withService(c) { lazy.value }
}
