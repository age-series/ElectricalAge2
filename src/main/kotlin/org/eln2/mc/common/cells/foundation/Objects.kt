package org.eln2.mc.common.cells.foundation

import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Component
import org.ageseries.libage.sim.thermal.ConnectionParameters
import org.ageseries.libage.sim.thermal.Simulator
import org.eln2.mc.ThermalBody
import org.eln2.mc.data.LocationDescriptor
import org.eln2.mc.data.LocatorRelationRuleSet
import org.eln2.mc.data.requireBlockPosLoc
import org.eln2.mc.directionTo
import org.eln2.mc.mathematics.Vector3di
import org.eln2.mc.mathematics.exp2i
import org.eln2.mc.scientific.DiffusionFluidOptions
import org.eln2.mc.scientific.RealizedVoxelPatchNode
import org.eln2.mc.scientific.VoxelPatchDirection
import org.eln2.mc.scientific.VoxelPatchModule

/**
 * Represents a discrete simulation unit that participates in one simulation type.
 * It can connect to other objects of the same simulation type.
 * */
abstract class SimulationObject(val cell: Cell) {
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

    open fun acceptsRemoteLocation(remoteDesc: LocationDescriptor): Boolean {
        return ruleSet.accepts(cell.pos.descriptor, remoteDesc)
    }
}

data class ThermalComponentInfo(val body: ThermalBody)
abstract class ThermalObject(cell: Cell) : SimulationObject(cell) {
    var simulation: Simulator? = null
        private set

    protected val connections = ArrayList<ThermalObject>()

    final override val type = SimulationObjectType.Thermal

    open val maxConnections = Int.MAX_VALUE

    protected fun indexOf(obj: ThermalObject): Int {
        val index = connections.indexOf(obj)

        if (index == -1) {
            error("Connections did not have $obj")
        }

        return index
    }

    /**
     * Called by the cell graph to fetch a connection candidate.
     * */
    abstract fun offerComponent(neighbour: ThermalObject): ThermalComponentInfo

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
    open fun addConnection(connectionInfo: ThermalObject) {
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
abstract class ElectricalObject(cell: Cell) : SimulationObject(cell) {
    /**
     * The circuit this object is part of.
     * It is initialized while the solver is being built.
     * @see setNewCircuit
     * */
    var circuit: Circuit? = null
        private set

    protected val connections = ArrayList<ElectricalObject>()

    final override val type = SimulationObjectType.Electrical

    /**
     * This is used to validate new connections.
     * If more connections than what is specified are created, an error will occur.
     * */
    open val maxConnections = Int.MAX_VALUE

    protected fun indexOf(obj: ElectricalObject): Int {
        val index = connections.indexOf(obj)

        if (index == -1) {
            error("Connections did not have $obj")
        }

        return index
    }

    /**
     * Called by electrical objects to fetch a connection candidate.
     * */
    abstract fun offerComponent(neighbour: ElectricalObject): ElectricalComponentInfo

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
    open fun addConnection(remoteObj: ElectricalObject) {
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
     * */
    protected abstract fun addComponents(circuit: Circuit)

    override fun build() {
        // Suggested by Grissess (and should have crossed my mind too, shame on me):
        connections.forEach { remote ->
            val localInfo = offerComponent(remote)
            val remoteInfo = remote.offerComponent(this)
            localInfo.component.connect(localInfo.index, remoteInfo.component, remoteInfo.index)
        }
    }
}

data class DiffusionDef(
    val name: String,
    val simulationOptions: DiffusionFluidOptions,
)

interface DiffusionAccessor {
    val realizedNode: RealizedVoxelPatchNode
    val node get() = realizedNode.node
    val patch get() = node.patch

    fun readDensity(tilePatch: Vector3di): Float
    fun addDensityIncr(tilePatch: Vector3di, incr: Float, activate: Boolean = true)
    fun setDensity(tilePatch: Vector3di, amount: Float, activate: Boolean = true)
    fun activate(tilePatch: Vector3di)
}

abstract class DiffusionObject(cell: Cell, val patchLog: Int, val def: DiffusionDef) : SimulationObject(cell) {
    data class Connection(
        val obj: DiffusionObject,
        val direction: VoxelPatchDirection,
    )

    val size = exp2i(patchLog)

    protected val connections = ArrayList<Connection>()

    val actualConnections get() = connections as List<Connection>
    val connectionDirections get() = connections.map { it.direction }

    private var accessor: DiffusionAccessor? = null

    protected val simulation: DiffusionAccessor
        get() =
            this.accessor ?: error("Tried to access DRAGONS simulation accessor before it was ready")

    final override val type get() = SimulationObjectType.Diffusion

    fun isCompatibleWith(remote: DiffusionObject) = def == remote.def && size == remote.size

    open fun addConnection(remote: DiffusionObject) {
        require(isCompatibleWith(remote)) { "Tried to create incompatible connection" }
        require(!connections.any { it.obj == remote }) { "Duplicate connection" }

        val actualBlockPosWorld = cell.posDescr.requireBlockPosLoc {
            "DRAGONS requires an actual world position"
        }

        val targetBlockPosWorld = remote.cell.posDescr.requireBlockPosLoc {
            "DRAGONS requires a target world position"
        }

        val dxActualTarget = when (actualBlockPosWorld.directionTo(targetBlockPosWorld)) {
            Direction.DOWN -> VoxelPatchDirection.Down
            Direction.UP -> VoxelPatchDirection.Up
            Direction.NORTH -> VoxelPatchDirection.Back
            Direction.SOUTH -> VoxelPatchDirection.Front
            Direction.WEST -> VoxelPatchDirection.Left
            Direction.EAST -> VoxelPatchDirection.Right
            null -> error("Unhandled object direction $actualBlockPosWorld $targetBlockPosWorld")
        }

        if (connections.any { it.direction == dxActualTarget }) {
            error("Duplicate connection $dxActualTarget")
        }

        connections.add(Connection(remote, dxActualTarget))
    }

    override fun destroy() {
        connections.forEach { obj -> obj.obj.connections.removeIf { it.obj == this } }
    }

    override fun update(connectionsChanged: Boolean, graphChanged: Boolean) {}

    override fun clear() {
        connections.clear()
        accessor = null
    }

    fun createPatchModule(): VoxelPatchModule {
        val module = VoxelPatchModule(size)

        createVolumetricDefinition(module)

        return module
    }

    fun bindAccessor(accessor: DiffusionAccessor) {
        this.accessor = accessor
    }

    protected abstract fun createVolumetricDefinition(module: VoxelPatchModule)

    override fun build() {}
}

/**
 * Represents an object with NBT saving capabilities.
 * */
interface PersistentObject {
    fun save(): CompoundTag
    fun load(tag: CompoundTag)
}

class SimulationObjectSet(objects: List<SimulationObject>) {
    constructor(vararg objects: SimulationObject) : this(objects.asList())

    private val objects = HashMap<SimulationObjectType, SimulationObject>()

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

    private fun getObject(type: SimulationObjectType): SimulationObject {
        return objects[type] ?: error("Object set does not have $type")
    }

    val electricalObject get() = getObject(SimulationObjectType.Electrical) as ElectricalObject
    val thermalObject get() = getObject(SimulationObjectType.Thermal) as ThermalObject
    val diffusionObject get() = getObject(SimulationObjectType.Diffusion) as DiffusionObject

    fun process(function: ((SimulationObject) -> Unit)) {
        objects.values.forEach(function)
    }

    operator fun get(type: SimulationObjectType): SimulationObject {
        return objects[type] ?: error("Object set does not have $type")
    }
}

enum class SimulationObjectType(id: Int, name: String) {
    Electrical(1, "electrical"),
    Thermal(2, "thermal"),
    Diffusion(3, "diffusion")
}
