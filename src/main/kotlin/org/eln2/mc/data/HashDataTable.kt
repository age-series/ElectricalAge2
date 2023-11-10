package org.eln2.mc.data

import mcp.mobius.waila.api.IPluginConfig
import org.ageseries.libage.sim.thermal.Temperature
import org.eln2.mc.integration.WailaEntity
import org.eln2.mc.integration.WailaTooltipBuilder

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

fun node(cfg: (HashDataNode) -> Unit): HashDataNode = HashDataNode().also(cfg)

fun data(cfg: (HashDataTable) -> Unit): HashDataNode = node { cfg(it.data) }

class HashDataNode(val data: HashDataTable) {
    constructor() : this(HashDataTable())

    val children = ArrayList<HashDataNode>()

    fun withChild(child: HashDataNode): HashDataNode {
        require(!children.contains(child)) { "Duplicate container child" }
        children.add(child)

        return this
    }

    fun withChild(child: HashDataTable): HashDataNode = withChild(HashDataNode(child))

    fun withChild(action: (HashDataNode) -> Unit): HashDataNode {
        withChild(HashDataNode().also(action))
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

    inline fun <reified F> pull(other: HashDataNode) {
        require(other.data.contains<F>()) { "$other did not have ${F::class.java}"}
        data.withField { other.data.get<F>() }
    }

    inline fun <reified F> pull(child: DataContainer) = pull<F>(child.dataNode)
}

interface DataContainer {
    val dataNode: HashDataNode
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

    fun readKelvin() = read().kelvin
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
    constructor(self: HashDataTable, inspect: Boolean = true) : this(inspect, {
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
