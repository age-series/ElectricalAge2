package org.eln2.mc.utility

import java.util.concurrent.ConcurrentHashMap

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
