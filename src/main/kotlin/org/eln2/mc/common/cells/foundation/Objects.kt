package org.eln2.mc.common.cells.foundation

import net.minecraft.nbt.CompoundTag
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Component
import org.ageseries.libage.sim.thermal.ConnectionParameters
import org.ageseries.libage.sim.thermal.Simulator
import org.eln2.mc.*
import org.eln2.mc.data.*

/**
 * Represents a discrete simulation unit that participates in one simulation type.
 * It can connect to other objects of the same simulation type.
 * */
abstract class SimulationObject<C : Cell>(val cell: C) {
    abstract val type: SimulationObjectType

    private val rsLazy = lazy { LocatorRelationRuleSet() }

    val ruleSet get() = rsLazy.value

    /**
     * Called when the connections and/or graph changes.
     * */
    abstract fun update(connectionsChanged: Boolean, graphChanged: Boolean)

    /**
     * Called when the solver is being built.
     * Here, the previous state should be cleared so that the object is ready to join a new simulation.
     * */
    abstract fun clear()

    /**
     * Called when the solver is being built, after *clear*.
     * */
    abstract fun build()

    /**
     * Called when the cell is destroyed.
     * Connections should be removed here.
     * */
    abstract fun destroy()

    open fun acceptsRemoteLocation(remoteDesc: Locator): Boolean {
        return ruleSet.accepts(cell.locator, remoteDesc)
    }
}

data class ThermalComponentInfo(val body: ThermalBody)

interface ThermalContactInfo {
    fun getContactTemperature(other: Locator) : Double?
}

abstract class ThermalObject<C : Cell>(cell: C) : SimulationObject<C>(cell) {
    var simulation: Simulator? = null
        private set

    protected val connections = ArrayList<ThermalObject<*>>()

    final override val type = SimulationObjectType.Thermal

    open val maxConnections = Int.MAX_VALUE

    protected fun indexOf(obj: ThermalObject<*>): Int {
        val index = connections.indexOf(obj)

        if (index == -1) {
            error("Connections did not have $obj")
        }

        return index
    }

    /**
     * Called by the cell graph to fetch a connection candidate.
     * */
    abstract fun offerComponent(neighbour: ThermalObject<*>): ThermalComponentInfo

    /**
     * Called by the building logic when the thermal object is made part of a simulation.
     * Also calls the *registerComponents* method.
     * */
    fun setNewSimulation(simulator: Simulator) {
        this.simulation = simulator

        addComponents(simulator)
    }

    /**
     * Called by the cell when a valid connection candidate is discovered.
     * */
    open fun addConnection(connectionInfo: ThermalObject<*>) {
        require(!connections.contains(connectionInfo)) { "Duplicate connection" }
        connections.add(connectionInfo)

        if (connections.size > maxConnections) {
            error("Thermal object received more connections than were allowed")
        }
    }

    /**
     * Called when this object is destroyed. Connections are also cleaned up.
     * */
    override fun destroy() {
        connections.forEach { it.connections.remove(this) }
    }

    override fun update(connectionsChanged: Boolean, graphChanged: Boolean) {}

    override fun clear() {
        connections.clear()
    }

    override fun build() {
        if (simulation == null) {
            error("Tried to build thermal obj with null simulation")
        }

        connections.forEach { remote ->
            assert(remote.simulation == simulation)

            simulation!!.connect(
                this.offerComponent(remote).body.thermal,
                remote.offerComponent(this).body.thermal,
                ConnectionParameters.DEFAULT
            )
        }
    }

    /**
     * Called when the simulation must be updated with the components owned by this object.
     * */
    protected abstract fun addComponents(simulator: Simulator)
}

data class ElectricalComponentInfo(val component: Component, val index: Int)

abstract class ElectricalObject<C : Cell>(cell: C) : SimulationObject<C>(cell) {
    /**
     * The circuit this object is part of.
     * It is initialized while the solver is being built.
     * @see setNewCircuit
     * */
    var circuit: Circuit? = null
        private set

    protected val connections = ArrayList<ElectricalObject<*>>()

    final override val type = SimulationObjectType.Electrical

    /**
     * This is used to validate new connections.
     * If more connections than what is specified are created, an error will occur.
     * */
    open val maxConnections = Int.MAX_VALUE

    protected fun indexOf(obj: ElectricalObject<*>): Int {
        val index = connections.indexOf(obj)

        if (index == -1) {
            error("Connections did not have $obj")
        }

        return index
    }

    /**
     * Called by electrical objects to fetch a connection candidate.
     * The same component and pin **must** be returned by subsequent calls to this method, during same re-building moment.
     * */
    abstract fun offerComponent(neighbour: ElectricalObject<*>): ElectricalComponentInfo

    /**
     * Called by the building logic when the electrical object is made part of a circuit.
     * Also calls the *registerComponents* method.
     * */
    fun setNewCircuit(circuit: Circuit) {
        this.circuit = circuit

        addComponents(circuit)
    }

    /**
     * Called by the cell when a valid connection candidate is discovered.
     * */
    open fun addConnection(remoteObj: ElectricalObject<*>) {
        require(!connections.contains(remoteObj)) { "Duplicate connection" }

        connections.add(remoteObj)

        if (connections.size > maxConnections) {
            error("Electrical object received more connections than were allowed")
        }
    }

    /**
     * Called when this object is destroyed. Connections are also cleaned up.
     * */
    override fun destroy() {
        connections.forEach { it.connections.remove(this) }
    }

    override fun update(connectionsChanged: Boolean, graphChanged: Boolean) {}

    override fun clear() {
        connections.clear()
        clearComponents()
    }

    /**
     * Called when the solver is being built, and the components need to be re-created (or refreshed)
     * The connections are not available at this stage.
     * */
    protected abstract fun clearComponents()

    /**
     * Called when the circuit must be updated with the components owned by this object.
     * This is called before build.
     * By default, offers for all [connections] are gathered using [offerComponent], and the offered components are all added to the [circuit]
     * */
    protected open fun addComponents(circuit: Circuit) {
        val components = HashSet<Component>(2)

        connections.forEach { connection ->
            val offer = offerComponent(connection)
            components.add(offer.component)
        }

        components.forEach { component ->
            circuit.add(component)
        }
    }

    /**
     * Builds the connections, after the circuit was acquired in [setNewCircuit] and the components were added in [addComponents].
     * By default, offers for all [connections] are gathered using [offerComponent], and the components are connected using the pins indicated in the offers.
     * */
    override fun build() {
        // Suggested by Grissess (and should have crossed my mind too, shame on me):
        connections.forEach { remote ->
            val localInfo = offerComponent(remote)
            val remoteInfo = remote.offerComponent(this)
            localInfo.component.connect(localInfo.index, remoteInfo.component, remoteInfo.index)
        }
    }
}

/**
 * Represents an object with NBT saving capabilities.
 * */
interface PersistentObject {
    fun saveObjectNbt(): CompoundTag
    fun loadObjectNbt(tag: CompoundTag)
}

class SimulationObjectSet(objects: List<SimulationObject<*>>) {
    constructor(vararg objects: SimulationObject<*>) : this(objects.asList())

    private val objects = HashMap<SimulationObjectType, SimulationObject<*>>()

    init {
        objects.forEach {
            if (this.objects.put(it.type, it) != null) {
                error("Duplicate object of type ${it.type}")
            }
        }
    }

    fun hasObject(type: SimulationObjectType): Boolean {
        return objects.contains(type)
    }

    private fun getObject(type: SimulationObjectType): SimulationObject<*> {
        return objects[type] ?: error("Object set does not have $type")
    }

    fun getObjectOrNull(type: SimulationObjectType): SimulationObject<*>? {
        return objects[type]
    }

    val electricalObject get() = getObject(SimulationObjectType.Electrical) as ElectricalObject

    val thermalObject get() = getObject(SimulationObjectType.Thermal) as ThermalObject

    fun forEachObject(function: ((SimulationObject<*>) -> Unit)) {
        objects.values.forEach(function)
    }

    operator fun get(type: SimulationObjectType): SimulationObject<*> {
        return objects[type] ?: error("Object set does not have $type")
    }
}

enum class SimulationObjectType(val index: Int, val id: Int, val domain: String) {
    Electrical(0, 1, "electrical"),
    Thermal(1, 2, "thermal");

    companion object {
        val values: List<SimulationObjectType> = values().toList()
    }
}

interface ThermalBipole {
    val b1: ThermalBody
    val b2: ThermalBody
}

/**
 * Thermal object with two connection sides.
 * */
@NoInj
class ThermalBipoleObject<C : Cell>(
    cell: C,
    val map: PoleMap,
    b1Def: ThermalBodyDef,
    b2Def: ThermalBodyDef
) : ThermalObject<C>(cell), ThermalBipole, ThermalContactInfo {
    override var b1 = b1Def.create()
    override var b2 = b2Def.create()

    init {
        cell.environmentData.getOrNull<EnvironmentalTemperatureField>()?.readTemperature()?.also {
            b1.temperature = it
            b2.temperature = it
        }
    }

    override fun offerComponent(neighbour: ThermalObject<*>) = ThermalComponentInfo(
        when (map.evaluate(cell.locator, neighbour.cell.locator)) {
            Pole.Plus -> b1
            Pole.Minus -> b2
        }
    )

    override fun addComponents(simulator: Simulator) {
        simulator.add(b1)
        simulator.add(b2)
    }

    override fun getContactTemperature(other: Locator): Double? {
        val direction = map.evaluateOrNull(this.cell.locator, other)
            ?: return null

        return when(direction) {
            Pole.Plus -> b1.temperatureKelvin
            Pole.Minus -> b2.temperatureKelvin
        }
    }
}
