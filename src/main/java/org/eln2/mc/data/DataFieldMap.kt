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
