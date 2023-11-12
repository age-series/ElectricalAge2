package org.eln2.mc.data

fun interface DataGetter<T> {
    fun getValue(): T
}

class HashDataTable {
    private val fields = HashMap<Class<*>, DataGetter<*>>()

    val values get() = fields.values.toList()

    fun <T> withField(c: Class<T>, access: DataGetter<T>): HashDataTable {
        if (fields.put(c, access) != null) {
            error("Duplicate field $c")
        }

        return this
    }

    inline fun <reified T> withField(access: DataGetter<T>): HashDataTable = withField(T::class.java, access)

    inline fun <reified T> withField(const: T) = withField { const }

    fun withFields(vararg constants: Any) {
        constants.forEach {
            withField(it.javaClass) { it }
        }
    }

    private fun getGetter(c: Class<*>): DataGetter<*>? = fields[c]

    @Suppress("UNCHECKED_CAST")
    fun <T> getOrNull(c: Class<T>): T? = (getGetter(c) as? DataGetter<T>)?.getValue()

    inline fun <reified T> getOrNull(): T? = getOrNull(T::class.java)

    inline fun <reified T> get(): T = getOrNull<T>() ?: error("Table did not have ${T::class.java}")

    fun contains(c: Class<*>) = fields.containsKey(c)

    inline fun <reified T> contains(): Boolean = contains(T::class.java)
}
