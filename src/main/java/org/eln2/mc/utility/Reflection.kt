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

fun interface ServiceProvider<T> {
    fun getInstance(): T
}

fun interface ExternalResolver {
    fun resolve(c: Class<*>): Any?
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CONSTRUCTOR)
annotation class Inj
class ServiceCollection {
    private val services = HashMap<Class<*>, ServiceProvider<*>>()
    private val externalResolvers = ArrayList<ExternalResolver>()

    fun<T> withService(c: Class<T>, provider: ServiceProvider<T>): ServiceCollection {
        if(services.put(c, provider) != null) {
            error("Duplicate service $c")
        }

        return this
    }

    inline fun<reified T> withService(provider: ServiceProvider<T>) = withService(T::class.java, provider)

    fun withExternalResolver(r: ExternalResolver): ServiceCollection {
        externalResolvers.add(r)
        return this
    }

    fun resolve(c: Class<*>): Any? {
        var instance =  services[c]?.getInstance()

        if(instance != null) {
            return instance
        }

        externalResolvers.forEach {
            instance = it.resolve(c)

            if(instance != null) {
                return instance
            }
        }

        return null
    }

    fun activate(c: Class<*>, extraParams: List<Any>): Any {
        val rxTargets = activators.getOrPut(c) {
            val constructors = if(c.constructors.any { it.getAnnotation(Inj::class.java) != null }) {
                c.constructors.filter { it.getAnnotation(Inj::class.java) != null }
            }
            else {
                c.constructors.toList()
            }

            ArrayList(
                constructors.map { ctor ->
                    val args = ArrayList<Class<*>>()

                    ctor.parameters.forEach {
                        val pClass = it.parameterizedType as Class<*>

                        if(args.contains(pClass)) {
                            error("Ambiguous inject parameter $it")
                        }

                        args.add(pClass)
                    }

                    ActivatorRsi(ArrayList(args)) { inst -> ctor.newInstance(*inst) }
                }
            )
        }

        rxTargets.forEach { rsi ->
            val args = Array<Any?>(rsi.parameters.size) { null }

            rsi.parameters.forEachIndexed { index, paramClass ->
                args[index] = resolve(paramClass)
                    ?: extraParams.firstOrNull { it.javaClass == paramClass }
                        ?: return@forEach
            }

            return rsi.activator(args)
        }

        error("Failed to solve constructor for $c")
    }

    inline fun<reified T> activate(extraParams: List<Any>): T = activate(T::class.java, extraParams) as T

    private data class ActivatorRsi(
        val parameters: ArrayList<Class<*>>,
        val activator: (Array<Any?>) -> Any
    )

    companion object {
        private val activators = ConcurrentHashMap<Class<*>, ArrayList<ActivatorRsi>>()
    }
}

inline fun<reified T> ServiceCollection.withSingleton(noinline resolver: () -> T): ServiceCollection {
    val lazy = lazy(resolver)
    return this.withService { lazy.value }
}

fun<T> ServiceCollection.withSingleton(c: Class<T>, resolver: () -> T): ServiceCollection {
    val lazy = lazy(resolver)
    return this.withService(c) { lazy.value }
}
