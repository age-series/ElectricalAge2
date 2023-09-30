package org.eln2.mc.data

import mcp.mobius.waila.api.IPluginConfig
import org.ageseries.libage.sim.thermal.Temperature
import org.eln2.mc.integration.WailaEntity
import org.eln2.mc.integration.WailaTooltipBuilder

fun interface DataGetter<T> {
    fun getValue(): T
}

class DataTable {
    private val fields = HashMap<Class<*>, DataGetter<*>>()

    val values get() = fields.values.toList()

    fun <T> withField(c: Class<T>, access: DataGetter<T>): DataTable {
        if (fields.put(c, access) != null) {
            error("Duplicate field $c")
        }

        return this
    }

    inline fun <reified T> withField(access: DataGetter<T>): DataTable = withField(T::class.java, access)

    inline fun <reified T> withField(const: T) = withField { const }

    fun withFields(vararg constants: Any) {
        constants.forEach {
            withField(it.javaClass) { it }
        }
    }

    private fun getAccessor(c: Class<*>): DataGetter<*>? = fields[c]

    @Suppress("UNCHECKED_CAST")
    fun <T> getOrNull(c: Class<T>): T? = (getAccessor(c) as? DataGetter<T>)?.getValue()

    inline fun <reified T> getOrNull(): T? = getOrNull(T::class.java)

    inline fun <reified T> get(): T = getOrNull<T>() ?: error("Table did not have ${T::class.java}")

    fun contains(c: Class<*>) = fields.containsKey(c)

    inline fun <reified T> contains(): Boolean = contains(T::class.java)
}

inline fun <reified T1, reified T2> DataTable.getPair(): Pair<T1, T2>? {
    return Pair(
        this.getOrNull<T1>() ?: return null,
        this.getOrNull<T2>() ?: return null
    )
}

fun node(cfg: (DataNode) -> Unit): DataNode = DataNode().also(cfg)

fun data(cfg: (DataTable) -> Unit): DataNode = node { cfg(it.data) }

class DataNode(val data: DataTable) {
    constructor() : this(DataTable())

    val children = ArrayList<DataNode>()

    fun withChild(child: DataNode): DataNode {
        require(!children.contains(child)) { "Duplicate container child" }
        children.add(child)

        return this
    }

    fun withChild(child: DataTable): DataNode = withChild(DataNode(child))

    fun withChild(action: (DataNode) -> Unit): DataNode {
        withChild(DataNode().also(action))
        return this
    }

    fun <T> fieldScan(c: Class<T>): ArrayList<T> {
        val results = ArrayList<T>()

        data.getOrNull(c)?.also(results::add)
        children.forEach { results.addAll(it.fieldScan(c)) }

        return results
    }

    fun valueScan(consumer: (Any) -> Unit) {
        data.values.forEach { consumer(it.getValue() ?: return@forEach) }
        children.forEach { it.valueScan(consumer) }
    }

    fun valueScanChildren(consumer: (Any) -> Unit) {
        children.forEach { it.valueScan(consumer) }
    }

    inline fun <reified T> fieldScan(): ArrayList<T> = fieldScan(T::class.java)

    inline fun <reified F> pull(other: DataNode) {
        require(other.data.contains<F>()) { "$other did not have ${F::class.java}"}
        data.withField { other.data.get<F>() }
    }

    inline fun <reified F> pull(child: DataEntity) = pull<F>(child.dataNode)
}

interface DataEntity {
    val dataNode: DataNode
}

data class VoltageField(var inspect: Boolean = true, val read: () -> Double) : WailaEntity {
    override fun appendWaila(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        if (inspect) {
            builder.voltage(read())
        }
    }
}

data class CurrentField(var inspect: Boolean = true, val read: () -> Double) : WailaEntity {
    override fun appendWaila(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        if (inspect) {
            builder.current(read())
        }
    }
}

data class TemperatureField(var inspect: Boolean = true, val read: () -> Temperature) : WailaEntity {
    override fun appendWaila(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        if (inspect) {
            builder.temperature(read().kelvin)
        }
    }

    fun readK() = read().kelvin
}

data class EnergyField(var inspect: Boolean = true, val read: () -> Double) : WailaEntity {
    override fun appendWaila(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        if (inspect) {
            builder.energy(read())
        }
    }
}

data class MassField(var inspect: Boolean = true, val read: () -> Double) : WailaEntity {
    override fun appendWaila(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        if (inspect) {
            builder.mass(read())
        }
    }
}

data class PowerField(var inspect: Boolean = true, val read: () -> Double) : WailaEntity {
    constructor(self: DataTable, inspect: Boolean = true) : this(inspect, {
        // Maybe blow up?
        val vf = self.getOrNull<VoltageField>()?.read?.invoke() ?: 0.0
        val cf = self.getOrNull<CurrentField>()?.read?.invoke() ?: 0.0
        vf * cf
    })

    override fun appendWaila(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        if (inspect) {
            builder.power(read())
        }
    }
}

data class ResistanceField(var inspect: Boolean = true, val read: () -> Double) : WailaEntity {
    override fun appendWaila(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        if (inspect) {
            builder.resistance(read())
        }
    }
}

data class ObjectField(val name: String, var inspect: Boolean = true, val read: () -> Any?) : WailaEntity {
    override fun appendWaila(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        if (inspect) {
            read()?.also {
                builder.text(name, it)
            }
        }
    }
}

data class TooltipField(val submit: (builder: WailaTooltipBuilder, config: IPluginConfig?) -> Unit) : WailaEntity {
    constructor(submit: (builder: WailaTooltipBuilder) -> Unit) : this({ b, _ -> submit(b) })

    override fun appendWaila(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        submit(builder, config)
    }
}
