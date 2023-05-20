package org.eln2.mc.data

fun interface IDataFieldAccessor<T> {
    fun getField(): T
}

class DataAccess {
    private val fields = HashMap<Class<*>, IDataFieldAccessor<*>>()

    fun <T> withField(c: Class<T>, access: IDataFieldAccessor<T>): DataAccess {
        if(fields.put(c, access) != null) {
            error("Duplicate field $c")
        }

        return this
    }

    inline fun<reified T> withField(access: IDataFieldAccessor<T>): DataAccess = withField(T::class.java, access)

    private fun getAccessor(c: Class<*>): IDataFieldAccessor<*>? = fields[c]
    fun<T> read(c: Class<T>): T? = (getAccessor(c) as? IDataFieldAccessor<T>)?.getField()
    inline fun<reified T> read(): T? = read(T::class.java)
}

class DataAccessNode(val data: DataAccess) {
    constructor() : this(DataAccess())

    val children = ArrayList<DataAccessNode>()

    fun withChild(child: DataAccessNode): DataAccessNode {
        require(!children.contains(child)) { "Duplicate container child" }
        children.add(child)

        return this
    }

    fun withChild(child: DataAccess): DataAccessNode = withChild(DataAccessNode(child))

    fun<T> fieldScan(c: Class<T>): ArrayList<T> {
        val results = ArrayList<T>()

        data.read(c)?.also(results::add)
        children.forEach { results.addAll(it.fieldScan(c)) }

        return results
    }

    inline fun<reified T> fieldScan(): ArrayList<T> = fieldScan(T::class.java)
}
interface IDataEntity {
    val dataAccessNode: DataAccessNode
}

interface INameField {
    val name: String
}
