package org.eln2.mc.data

import mcp.mobius.waila.api.IPluginConfig
import org.ageseries.libage.sim.thermal.Temperature
import org.eln2.mc.integration.WailaEntity
import org.eln2.mc.integration.WailaTooltipBuilder

fun interface DataFieldGetter<T> {
    fun getValue(): T
}

class DataFieldMap {
    private val fields = HashMap<Class<*>, DataFieldGetter<*>>()

    val values get() = fields.values.toList()

    fun <T> withField(c: Class<T>, access: DataFieldGetter<T>): DataFieldMap {
        if(fields.put(c, access) != null) {
            error("Duplicate field $c")
        }

        return this
    }

    inline fun<reified T> withField(access: DataFieldGetter<T>): DataFieldMap = withField(T::class.java, access)
    inline fun<reified T> withField(const: T) = withField { const }
    fun withFields(vararg constants: Any) {
        constants.forEach {
            withField(it.javaClass) { it }
        }
    }

    private fun getAccessor(c: Class<*>): DataFieldGetter<*>? = fields[c]
    @Suppress("UNCHECKED_CAST")

    fun<T> read(c: Class<T>): T? = (getAccessor(c) as? DataFieldGetter<T>)?.getValue()
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

    fun withChild(action: (DataNode) -> Unit) {
        withChild(DataNode().also(action))
    }

    fun<T> fieldScan(c: Class<T>): ArrayList<T> {
        val results = ArrayList<T>()

        data.read(c)?.also(results::add)
        children.forEach { results.addAll(it.fieldScan(c)) }

        return results
    }

    fun valueScan(consumer: (Any) -> Unit) {
        data.values.forEach { consumer(it.getValue() ?: return@forEach) }
        children.forEach { it.valueScan(consumer) }
    }

    inline fun<reified T> fieldScan(): ArrayList<T> = fieldScan(T::class.java)
}

interface DataEntity {
    val dataNode: DataNode
}

data class VoltageField(val read: () -> Double): WailaEntity {
    override fun appendBody(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        builder.voltage(read())
    }
}

data class CurrentField(val read: () -> Double): WailaEntity {
    override fun appendBody(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        builder.current(read())
    }
}

data class TemperatureField(val read: () -> Temperature): WailaEntity {
    override fun appendBody(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        builder.temperature(read().kelvin)
    }
}

data class PowerField(val read: () -> Double): WailaEntity {
    constructor(self: DataFieldMap) : this({
        // Maybe blow up?
        val vf = self.read<VoltageField>()?.read?.invoke() ?: 0.0
        val cf = self.read<CurrentField>()?.read?.invoke() ?: 0.0
        vf * cf
    })

    override fun appendBody(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        builder.power(read())
    }
}

data class ResistanceField(val read: () -> Double): WailaEntity {
    override fun appendBody(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        builder.resistance(read())
    }
}

data class ObjectField(val name: String, val read: () -> Any?): WailaEntity {
    override fun appendBody(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        read()?.also {
            builder.text(name, it)
        }
    }
}

data class TooltipField(val submit: (builder: WailaTooltipBuilder, config: IPluginConfig?) -> Unit): WailaEntity {
    constructor(submit: (builder: WailaTooltipBuilder) -> Unit) : this({ b, _ -> submit(b)})

    override fun appendBody(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        submit(builder, config)
    }
}
