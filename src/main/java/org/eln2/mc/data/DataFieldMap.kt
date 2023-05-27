package org.eln2.mc.data

fun interface DataFieldAccessor<T> {
    fun getField(): T
}

class DataFieldMap {
    private val fields = HashMap<Class<*>, DataFieldAccessor<*>>()

    fun <T> withField(c: Class<T>, access: DataFieldAccessor<T>): DataFieldMap {
        if(fields.put(c, access) != null) {
            error("Duplicate field $c")
        }

        return this
    }

    inline fun<reified T> withField(access: DataFieldAccessor<T>): DataFieldMap = withField(T::class.java, access)
    private fun getAccessor(c: Class<*>): DataFieldAccessor<*>? = fields[c]
    fun<T> read(c: Class<T>): T? = (getAccessor(c) as? DataFieldAccessor<T>)?.getField()
    inline fun<reified T> read(): T? = read(T::class.java)
    fun has(c: Class<*>) = fields.containsKey(c)
    inline fun<reified T> has(): Boolean = has(T::class.java)
}

inline fun<reified T1, reified T2> DataFieldMap.readAll2(): Pair<T1, T2>? {
    return Pair(
        this.read<T1>() ?: return null,
        this.read<T2>() ?: return null)
}

inline fun<reified T1, reified T2, reified T3> DataFieldMap.readAll3(): Triple<T1, T2, T3>? {
    return Triple(
        this.read<T1>() ?: return null,
        this.read<T2>() ?: return null,
        this.read<T3>() ?: return null
    )
}

class DataNode(val data: DataFieldMap) {
    constructor() : this(DataFieldMap())

    val children = ArrayList<DataNode>()

    fun withChild(child: DataNode): DataNode {
        require(!children.contains(child)) { "Duplicate container child" }
        children.add(child)

        return this
    }

    fun withChild(child: DataFieldMap): DataNode = withChild(DataNode(child))

    fun<T> fieldScan(c: Class<T>): ArrayList<T> {
        val results = ArrayList<T>()

        data.read(c)?.also(results::add)
        children.forEach { results.addAll(it.fieldScan(c)) }

        return results
    }

    inline fun<reified T> fieldScan(): ArrayList<T> = fieldScan(T::class.java)
}

interface DataEntity {
    val dataNode: DataNode
}

interface NamedField {
    val name: String
}
