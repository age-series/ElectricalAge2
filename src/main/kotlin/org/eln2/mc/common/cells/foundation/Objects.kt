package org.eln2.mc.common.cells.foundation

import net.minecraft.nbt.CompoundTag
import org.ageseries.libage.data.Quantity
import org.ageseries.libage.data.Temperature
import org.ageseries.libage.mathematics.approxEq
import org.ageseries.libage.sim.ConnectionParameters
import org.ageseries.libage.sim.Pole
import org.ageseries.libage.sim.Simulator
import org.ageseries.libage.sim.SubSolverSet
import org.ageseries.libage.sim.ThermalMass
import org.ageseries.libage.sim.electrical.*
import org.ageseries.libage.sim.kinetic.*
import org.eln2.mc.MINUS
import org.eln2.mc.PLUS
import org.eln2.mc.common.grids.GridConnectionCell
import org.eln2.mc.PoleMap
import org.eln2.mc.evaluate
import org.eln2.mc.extensions.getQuantity
import org.eln2.mc.extensions.putQuantity
import org.eln2.mc.offerExternal
import org.eln2.mc.offerInternal
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Represents a discrete simulation unit that participates in one simulation type.
 * It can connect to other objects of the same simulation type.
 * */
abstract class SimulationObject<C : Cell>(val cell: C) {
    abstract val type: SimulationObjectType

    abstract fun getSubSolvers() : Iterable<Any>

    /**
     * Called when the connections and/or graph changes.
     * */
    open fun update(connectionsChanged: Boolean, graphChanged: Boolean) {
        if(graphChanged) {
            subscribe(cell.subscribers.persistentSimulation)
            subscribeServerThread(cell.subscribers.persistentServer)
        }
    }

    /**
     * Called when subscribers should be added, after the graph changes.
     * This is called after [Cell.subscribe]
     * */
    protected open fun subscribe(subscribers: SubscriberCollection<SimulationPhase>) { }

    /**
     * Called when subscribers should be added, after the graph changes.
     * These subscribers are for the game thread.
     * This is called after [Cell.subscribeServerThread]
     * */
    protected open fun subscribeServerThread(subscribers: SubscriberCollection<ServerPhase>) { }

    /**
     * Called when the solver is being built.
     * Here, the previous state should be cleared so that the object is ready to join a new simulation.
     * */
    abstract fun clear()

    /**
     * Called when the cell is destroyed.
     * Connections should be removed here.
     * */
    abstract fun destroy()
}

data class ThermalComponentInfo(val body: ThermalMass)

interface ThermalContactInfo {
    fun getContactTemperature(other: Cell) : Quantity<Temperature>?
}

abstract class ThermalObject<C : Cell>(cell: C) : SimulationObject<C>(cell) {
    var simulation: Simulator? = null
        private set

    override fun getSubSolvers() = simulation?.let { listOf(it) } ?: emptyList()

    val connections = ArrayList<ThermalObject<*>>()

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
    abstract fun offerComponent(remote: ThermalObject<*>): ThermalComponentInfo

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

    override fun clear() {
        connections.clear()
    }

    /**
     * Called when the simulation must be updated with the components owned by this object.
     * */
    protected abstract fun addComponents(simulator: Simulator)

    /**
     * Implements object-based rules. Defaults to calling predicates of the cell.
     * @param remote The remote thermal object.
     * @return True, if the connection is allowed. Otherwise, false.
     * */
    open fun acceptsRemoteObject(remote: ThermalObject<*>) : Boolean {
        return cell.thermalObjectPredicate(remote)
    }

    protected open fun getParameters(remote: ThermalObject<*>) = ConnectionParameters.DEFAULT

    open fun build() {
        if (simulation == null) {
            error("Tried to build thermal obj with null simulation")
        }

        connections.forEach { remote ->
            assert(remote.simulation == simulation)

            simulation!!.connect(
                this.offerComponent(remote).body,
                remote.offerComponent(this).body,
                getParameters(remote)
            )
        }
    }
}

abstract class ElectricalObject<C : Cell>(cell: C) : SimulationObject<C>(cell) {
    /**
     * The sub-solvers this object's network created.
     * This doesn't mean it contains exclusively the sub-solvers this object's [Term]s are part of.
     * Set in the last step of the building process, after [build].
     * */
    var subSolvers: SubSolverSet<ElectricalSimulation>? = null
        private set

    override fun getSubSolvers() = subSolvers?.solvers ?: emptyList()

    val connections = ArrayList<ElectricalObject<*>>()

    final override val type = SimulationObjectType.Electrical

    /**
     * Called by the cell when a valid connection candidate is discovered.
     * */
    open fun addConnection(remoteObj: ElectricalObject<*>) {
        require(!connections.contains(remoteObj)) {
            "Duplicate connection"
        }

        connections.add(remoteObj)
    }

    /**
     * Called when this object is destroyed. Connections are also cleaned up.
     * */
    override fun destroy() {
        connections.forEach { it.connections.remove(this) }
    }

    override fun clear() {
        connections.clear()
        clearComponents()
    }

    /**
     * Called when the solver is being built, and the components need to be re-created (or refreshed)
     * The connections are not available at this stage.
     * */
    protected open fun clearComponents() { }

    /**
     * Called by electrical objects to fetch a connection candidate.
     * The same component and pin **must** be returned by subsequent calls to this method, during same re-building moment.
     * */
    fun offerComponent(remote: ElectricalObject<*>) : ElectricalPin? {
        val remoteCell = remote.cell

        return if(remoteCell is GridConnectionCell) {
            offerTerminal(remoteCell, remoteCell.getFullMetadata(cell))
        }
        else {
            offerPolar(remote)
        }
    }

    /**
     * Called by electrical objects to fetch a connection candidate.
     * The same component and pin **must** be returned by subsequent calls to this method, during same re-building moment.
     * */
    protected open fun offerPolar(remote: ElectricalObject<*>): ElectricalPin? = null

    /**
     * Called by the grid connection cell's electrical object to fetch a connection candidate, for the specified terminal [m0].
     * The same component and pin **must** be returned by subsequent calls to this method, during same re-building moment.
     * */
    protected open fun offerTerminal(gc: GridConnectionCell, m0: GridConnectionCell.NodeInfo): ElectricalPin? = null

    /**
     * Called when the circuit must be updated with the components owned by this object.
     * This is called before build.
     * */
    open fun addComponents(circuit: ElectricalComponentSet) {
        for (remote in connections) {
            val offer = offerComponent(remote)
                ?: continue

            remote.offerComponent(this)
                ?: continue

            circuit.add(offer.component)
        }
    }

    /**
     * Implements object-based rules. Defaults to calling the cell's predicate.
     * @param remote The remote electrical object.
     * @return True, if the connection is allowed. Otherwise, false.
     * */
    open fun acceptsRemoteObject(remote: ElectricalObject<*>) : Boolean {
        return cell.electricalObjectPredicate(remote)
    }

    /**
     * Builds the connections.
     * */
    open fun build(map: ElectricalConnectivityMap) {
        for (remote in connections) {
            val localInfo = this.offerComponent(remote)
                ?: continue

            val remoteInfo = remote.offerComponent(this)
                ?: continue

            map.join(localInfo, remoteInfo)
        }
    }

    /**
     * Called after [build], once the circuits have been created.
     * */
    open fun setSubSolvers(subSolvers: SubSolverSet<ElectricalSimulation>) {
        this.subSolvers = subSolvers
    }
}

abstract class KineticObject<C : Cell>(cell: C) : SimulationObject<C>(cell) {
    /**
     * The sub-solvers this object's network created.
     * This doesn't mean it contains exclusively the sub-solvers this object's [org.ageseries.libage.sim.kinetic.KineticNode]s are part of.
     * Set in the last step of the building process, after [build].
     * */
    var subSolvers: SubSolverSet<KineticSimulation>? = null
        private set

    override fun getSubSolvers() = subSolvers?.solvers ?: emptyList()

    val connections = ArrayList<KineticObject<*>>()

    final override val type = SimulationObjectType.Kinetic

    /**
     * Called by the cell when a valid connection candidate is discovered.
     * */
    open fun addConnection(remoteObj: KineticObject<*>) {
        require(!connections.contains(remoteObj)) {
            "Duplicate connection"
        }

        connections.add(remoteObj)
    }

    /**
     * Called when the builder must be updated with the components owned by this object.
     * This is called before [build].
     * */
    abstract fun addNodes(builder: KineticNodeSet)

    /**
     * Called when this object is destroyed. Connections are also cleaned up.
     * */
    override fun destroy() {
        connections.forEach { it.connections.remove(this) }
    }

    override fun clear() {
        connections.clear()
        clearNodes()
    }

    /**
     * Called when the solver is being built, and the components need to be re-created (or refreshed)
     * The connections are not available at this stage.
     * */
    protected open fun clearNodes() { }

    /**
     * Called by kinetic objects to fetch a connection candidate.
     * The same extension **must** be returned by subsequent calls to this method, during same re-building moment.
     * */
    abstract fun offerExtension(remote: KineticObject<*>) : KineticExtension?

    protected fun KineticDouble.chooseExtension(map: PoleMap, remote: KineticObject<*>) = when(map.evaluateOrNull(cell, remote.cell)) {
        Pole.Positive -> this.plus()
        Pole.Negative -> this.minus()
        null -> null
    }
    /**
     * Implements object-based rules.
     * @param remote The remote kinetic object.
     * @return True, if the connection is allowed. Otherwise, false.
     * */
    open fun acceptsRemoteObject(remote: KineticObject<*>) : Boolean {
        return cell.kineticObjectPredicate(remote)
    }

    /**
     * Builds the constraints. After this, the sub-solvers will be realized and made available in [setSubSolvers].
     * */
    open fun build(map: KineticConstraintMap) {
        for (remote in connections) {
            val localExtension = this.offerExtension(remote)
                ?: continue

            val remoteExtension = remote.offerExtension(this)
                ?: continue

            map.join(localExtension, remoteExtension)
        }
    }

    /**
     * Called after [build], once the kinetic simulations have been created.
     * */
    open fun setSubSolvers(subSolvers: SubSolverSet<KineticSimulation>) {
        this.subSolvers = subSolvers
    }
}

/**
 * Represents an object with NBT saving capabilities.
 * */
interface PersistentObject {
    fun saveObjectNbt(): CompoundTag

    /**
     * Called when the graph is being loaded.
     * Connections are not available at this stage.
     * */
    fun loadObjectNbt(tag: CompoundTag)
}

class SimulationObjectSet(objects: List<SimulationObject<*>>) {
    private var electrical: ElectricalObject<*>? = null
    private var thermal: ThermalObject<*>? = null
    private var kinetic: KineticObject<*>? = null

    init {
        objects.forEach {
            when(it.type) {
                SimulationObjectType.Electrical -> {
                    if(electrical != null) {
                        error("Duplicate add electrical $electrical $it")
                    }

                    electrical = it as ElectricalObject<*>
                }
                SimulationObjectType.Thermal -> {
                    if(thermal != null) {
                        error("Duplicate add thermal $thermal $it")
                    }

                    thermal = it as ThermalObject<*>
                }
                SimulationObjectType.Kinetic -> {
                    if(kinetic != null) {
                        error("Duplicate add kinetic $kinetic $it")
                    }

                    kinetic = it as KineticObject<*>
                }
            }
        }
    }

    fun hasObject(type: SimulationObjectType) = when(type) {
        SimulationObjectType.Electrical -> electrical != null
        SimulationObjectType.Thermal -> thermal != null
        SimulationObjectType.Kinetic -> kinetic != null
    }

    fun getObjectOrNull(type: SimulationObjectType): SimulationObject<*>? = when(type) {
        SimulationObjectType.Electrical -> electrical
        SimulationObjectType.Thermal -> thermal
        SimulationObjectType.Kinetic -> kinetic
    }

    private fun getObject(type: SimulationObjectType) = getObjectOrNull(type) ?: error("Object set does not have $type")

    val electricalObject get() = electrical ?: error("Cannot get electrical object")
    val thermalObject get() = thermal ?: error("Cannot get thermal object")
    val kineticObject get() = kinetic ?: error("Cannot get kinetic object")

    @OptIn(ExperimentalContracts::class)
    fun forEachObject(function: ((SimulationObject<*>) -> Unit)) {
        contract {
            callsInPlace(function, InvocationKind.UNKNOWN)
        }

        if(electrical != null) function(electrical!!)
        if(thermal != null) function(thermal!!)
        if(kinetic != null) function(kinetic!!)
    }

    operator fun get(type: SimulationObjectType) = getObject(type)
}

enum class SimulationObjectType(val index: Int, val id: Int, val domain: String) {
    Electrical(0, 1, "electrical"),
    Thermal(1, 2, "thermal"),
    Kinetic(2, 3, "kinetic");
}

interface ThermalBipole {
    val b1: ThermalMass
    val b2: ThermalMass
}

/**
 * Thermal object made of two thermal masses.
 * @param map A map that maps the remote object to one of the two bodies.
 * @param b1 The body that maps to [Pole.Plus]
 * @param b2 The body that maps to [Pole.Minus]
 * */
class ThermalBipoleObject<C : Cell>(
    cell: C,
    val map: PoleMap,
    override val b1: ThermalMass,
    override val b2: ThermalMass,
    val b1Leakage: ConnectionParameters,
    val b2Leakage: ConnectionParameters
) : ThermalObject<C>(cell), PersistentObject, ThermalBipole, ThermalContactInfo {
    companion object {
        private const val B1 = "b1"
        private const val B2 = "b2"
    }

    private var lastTemperatureB1: Double
    private var lastTemperatureB2: Double

    init {
        cell.environmentData.loadTemperature(b1, b2)
        lastTemperatureB1 = !b1.temperature
        lastTemperatureB2 = !b2.temperature
    }

    override fun offerComponent(remote: ThermalObject<*>) = ThermalComponentInfo(
        when (map.evaluate(cell, remote.cell)) {
            Pole.Positive -> b1
            Pole.Negative -> b2
        }
    )

    override fun addComponents(simulator: Simulator) {
        simulator.add(b1)
        simulator.add(b2)
        simulator.connect(b1, cell.environmentData.ambientTemperature, b1Leakage)
        simulator.connect(b2, cell.environmentData.ambientTemperature, b2Leakage)
    }

    override fun getContactTemperature(other: Cell): Quantity<Temperature>? {
        val direction = map.evaluateOrNull(this.cell, other)
            ?: return null

        return when(direction) {
            Pole.Positive -> b1.temperature
            Pole.Negative -> b2.temperature
        }
    }

    override fun saveObjectNbt(): CompoundTag {
        val tag = CompoundTag()

        tag.putQuantity(B1, b1.temperature)
        tag.putQuantity(B2, b2.temperature)

        return tag
    }

    override fun loadObjectNbt(tag: CompoundTag) {
        b1.temperature = tag.getQuantity(B1)
        b2.temperature = tag.getQuantity(B2)
    }

    override fun subscribe(subscribers: SubscriberCollection<SimulationPhase>) {
        subscribers.addSubscriber(
            SubscriberOptions(100, SimulationPhase.Post),
            this::simulationTick
        )
    }

    private fun simulationTick(dt: Double, phase: SimulationPhase) {
        val flag = !b1.temperature.value.approxEq(lastTemperatureB1) || !b2.temperature.value.approxEq(lastTemperatureB2)

        cell.setChangedIf(flag) {
            lastTemperatureB1 = !b1.temperature
            lastTemperatureB2 = !b2.temperature
        }
    }
}

class PolarResistorObject<C : Cell>(cell: C, val map: PoleMap?) : ElectricalObject<C>(cell) {
    val component = Resistor()

    override fun addComponents(circuit: ElectricalComponentSet) {
        circuit.add(component)
    }

    override fun offerPolar(remote: ElectricalObject<*>) = if(map == null) null else when(map.evaluateOrNull(cell, remote.cell)) {
        Pole.Positive -> component.positive
        Pole.Negative -> component.negative
        null -> null
    }
}


class TerminalResistorObject<C : Cell>(cell: C, val plus: Int, val minus: Int) : ElectricalObject<C>(cell) {
    val component = Resistor()

    override fun addComponents(circuit: ElectricalComponentSet) {
        circuit.add(component)
    }


    override fun offerTerminal(gc: GridConnectionCell, m0: GridConnectionCell.NodeInfo) = when(m0.terminal) {
        plus -> component.positive
        minus -> component.negative
        else -> null
    }
}

/**
 * Electrical generator modeled with a resistor and a source of potential.
 * */
abstract class VRGObject<C : Cell>(cell: C) : ElectricalObject<C>(cell) {
    /**
     * Gets the resistor used by this object.
     * */
    val resistor = Resistor()

    /**
     * Gets the voltage source used by this object.
     * */
    val source = PotentialSource()

    /**
     * Adds [resistor] and [source] to the circuit, regardless of their connection status with other things.
     * */
    final override fun addComponents(circuit: ElectricalComponentSet) {
        circuit.add(resistor)
        circuit.add(source)
    }

    /**
     * Offers a term ref that should be used when connecting to an object on the positive pole of this source.
     * */
    protected fun plusOffer() = resistor.offerExternal()

    /**
     * Offers a term ref that should be used when connecting to an object on the negative pole of this source.
     * */
    protected fun minusOffer() = source.negative

    /**
     * Builds the sub-circuit and connects to the remote objects.
     * The sub-circuit looks like this:
     * ```
     * ▁▁ ▁▁▁▁▁▁ ▁▁
     * A?│+R-+V-│?B
     * ▔▔ ▔▔▔▔▔▔ ▔▔
     * ```
     * where *A* and *B* are some external objects, *+* is the positive pin, *-* is the negative pin, *R* is [resistor] and *V* is [source].
     * */
    final override fun build(map: ElectricalConnectivityMap) {
        map.join(resistor.offerInternal(), source.positive)
        super.build(map)
    }
}

/**
 * Generator model consisting of a [VoltageSource] + [Resistor], whose poles are mapped using a [PoleMap].
 * */
open class PolarVRGObject<C : Cell>(cell: C, val map: PoleMap) : VRGObject<C>(cell) {
    override fun offerPolar(remote: ElectricalObject<*>) =
        when (map.evaluateOrNull(this.cell, remote.cell)) {
            Pole.Positive -> plusOffer()
            Pole.Negative -> minusOffer()
            else -> null
        }
}

/**
 * Generator model consisting of a [PotentialSource] + [Resistor], whose poles are mapped to grid terminals.
 * */
open class TerminalVRGObject<C : Cell>(cell: C, val plus: Pole = Pole.Positive, val minus: Pole = Pole.Negative) : VRGObject<C>(cell) {
    /**
     * Gets the offered component by checking to see which terminal the [gc] is connected to.
     * @return
     *  The resistor's external pin when the terminal is [plus].
     *  The source's negative pin when the terminal is [minus].
     *  Null in any other case.
     * */
    override fun offerTerminal(gc: GridConnectionCell, m0: GridConnectionCell.NodeInfo) =
        when(m0.terminal) {
            PLUS -> plusOffer()
            MINUS -> minusOffer()
            else -> null
        }
}

/**
 * The voltage source object has a bundle of resistors, whose External Pins are exported to other objects, and
 * a voltage source, connected to the Internal Pins of the bundle.
 * */
class VoltageSourceObject(cell: Cell) : ElectricalObject<Cell>(cell) {
    val source = PotentialSource()
    val resistors = ResistorBundle(cell, 1e-4)

    override fun offerPolar(remote: ElectricalObject<*>) = resistors.getOfferedResistor(remote)

    override fun clearComponents() {
        resistors.clear()
    }

    override fun addComponents(circuit: ElectricalComponentSet) {
        circuit.add(source)
        resistors.addComponents(connections, circuit)
    }

    override fun build(map: ElectricalConnectivityMap) {
        map.ground(source.offerInternal())
        resistors.build(connections, this, map)

        resistors.forEach {
            map.join(it.offerInternal(), source.offerExternal())
        }
    }
}

class GroundObject(cell: Cell) : ElectricalObject<Cell>(cell) {
    val resistors = ResistorBundle(cell, 1e-5)

    override fun offerPolar(remote: ElectricalObject<*>) = resistors.getOfferedResistor(remote)

    override fun offerTerminal(gc: GridConnectionCell, m0: GridConnectionCell.NodeInfo) = resistors.getOfferedResistor(gc.electrical)

    override fun clearComponents() {
        resistors.clear()
    }

    override fun addComponents(circuit: ElectricalComponentSet) {
        resistors.addComponents(connections, circuit)
    }

    override fun build(map: ElectricalConnectivityMap) {
        resistors.build(connections, this, map)

        resistors.forEach {
            map.ground(it.offerInternal())
        }
    }
}

class PowerVoltageSourceObject<C : Cell>(cell: C, val map: PoleMap) : ElectricalObject<C>(cell) {
    val generator = PowerSource()
    val resistor = Resistor()

    override fun offerPolar(remote: ElectricalObject<*>) = when(map.evaluateOrNull(cell, remote.cell)) {
        Pole.Positive -> generator.positive
        Pole.Negative -> resistor.negative
        null -> null
    }

    override fun addComponents(circuit: ElectricalComponentSet) {
        circuit.add(generator)
        circuit.add(resistor)
    }

    override fun build(map: ElectricalConnectivityMap) {
        super.build(map)
        map.join(generator.negative, resistor.positive)
    }
}

const val SIGNAL_SERIES_RESISTANCE = 1e6
const val SIGNAL_COMPARE_RESISTANCE = 1e8

fun ElectricalComponentSet.add(signalSource: SignalSource) {
    this.add(signalSource.voltageSource)
    this.add(signalSource.resistor)
}

/**
 * Signal generator. Consists of a voltage source and a virtual resistor with high resistance.
 * This is an infinite energy source, but with a low power. The resistance is high; see [SIGNAL_SERIES_RESISTANCE].
 * Signals are always referenced to ground. As such, the [voltageSource]'s negative is grounded on [build].
 * The only valid offer is [offerOutput]. No other connections should be created with the components.
 */
class SignalSource {
    val voltageSource = PotentialSource()
    val resistor = Resistor().also { it.resistance = SIGNAL_SERIES_RESISTANCE }

    /**
     * Offers the signal output of this source. It is always the external pin of the [resistor].
     * */
    fun offerOutput() = resistor.offerExternal()

    fun build(map: ElectricalConnectivityMap) {
        map.join(voltageSource.positive, resistor.offerInternal())
        map.ground(voltageSource.negative)
    }

    /**
     * Sets the potential of the source.
     * */
    var signal: Double
        get() = voltageSource.potential
        set(value) { voltageSource.potential = value }
}
