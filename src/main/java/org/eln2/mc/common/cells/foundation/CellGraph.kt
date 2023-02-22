package org.eln2.mc.common.cells.foundation

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.server.ServerLifecycleHooks
import org.ageseries.libage.debug.dprintln
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.VoltageSource
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.annotations.CrossThreadAccess
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.common.cells.foundation.objects.SimulationObjectType
import org.eln2.mc.common.configs.Configuration
import org.eln2.mc.common.space.RelativeRotationDirection
import org.eln2.mc.extensions.NbtExtensions.getCellPos
import org.eln2.mc.extensions.NbtExtensions.getRelativeDirection
import org.eln2.mc.extensions.NbtExtensions.putCellPos
import org.eln2.mc.extensions.NbtExtensions.putRelativeDirection
import org.eln2.mc.utility.Time
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.ArrayDeque
import kotlin.system.measureNanoTime

fun interface ICellGraphSubscriber {
    fun simulationTick(elapsed: Double)
}

/**
 * The cell graph represents a physical network of cells.
 * It may have multiple simulation subsets, formed between objects in the cells of this graph.
 * The cell graph manages the solver and simulation.
 * It also has serialization/deserialization logic for saving to the disk using NBT.
 * */
class CellGraph(val id: UUID, val manager: CellGraphManager) {
    val cells = ArrayList<CellBase>()

    private val posCells = HashMap<CellPos, CellBase>()

    private val circuits = ArrayList<Circuit>()

    private val simulationStopLock = ReentrantLock()

    // This is the simulation task. It will be null if the simulation is stopped
    private var runningTask: ScheduledFuture<*>? = null

    val isRunning get() = runningTask != null

    @CrossThreadAccess
    private var updates = 0L

    private var updatesCheckpoint = 0L

    private val subscribers = ArrayList<ICellGraphSubscriber>()

    @CrossThreadAccess
    private val subscribersAddQueue = ConcurrentLinkedQueue<ICellGraphSubscriber>()

    @CrossThreadAccess
    private val subscribersRemoveQueue = ConcurrentLinkedQueue<ICellGraphSubscriber>()

    /**
     * Adds a subscriber to this graph, on the next simulation tick.
     * Subscribers are notified after each simulation step (from the simulation thread)
     * */
    @CrossThreadAccess
    fun addSubscriber(subscriber: ICellGraphSubscriber) {
        subscribersAddQueue.add(subscriber)
    }

    /**
     * Removes a subscriber from this graph, on the next simulation tick.
     * If it was not added beforehand, an error will occur in the simulation thread.
     * */
    @CrossThreadAccess
    fun enqueueRemoveSubscriber(subscriber: ICellGraphSubscriber) {
        subscribersRemoveQueue.add(subscriber)
    }

    fun removeSubscriber(subscriber: ICellGraphSubscriber) {
        validateMutationAccess()

        if(!subscribers.remove(subscriber)){
            error("Could not remove $subscriber")
        }
    }

    @CrossThreadAccess
    var lastTickTime = 0.0
        private set

    /**
     * Gets the number of updates that have occurred since the last call to this method.
     * */
    fun sampleElapsedUpdates(): Long {
        val elapsed = updates - updatesCheckpoint
        updatesCheckpoint += elapsed

        return elapsed
    }

    /**
     * True, if the solution was found last simulation tick. Otherwise, false.
     * */
    var successful = false
        private set

    /**
     * Checks if the simulation is running. Presumably, this is used by logic that wants to mutate the graph.
     * It also checks if the caller is the server thread.
     * */
    private fun validateMutationAccess() {
        if (runningTask != null) {
            error("Tried to mutate the simulation while it was running")
        }

        if (Thread.currentThread() != ServerLifecycleHooks.getCurrentServer().runningThread) {
            error("Illegal cross-thread access into the cell graph")
        }
    }

    /**
     * Runs one simulation step. This is called from the update thread.
     * */
    @CrossThreadAccess
    private fun update() {
        simulationStopLock.lock()

        while (true) {
            val subscriber = subscribersAddQueue.poll()
                ?: break

            subscribers.add(subscriber)
        }

        while (true) {
            val subscriber = subscribersRemoveQueue.poll()
                ?: break

            if(!subscribers.remove(subscriber)){
                error("Could not find subscriber $subscriber")
            }
        }

        val elapsed = 0.05

        lastTickTime = Time.toSeconds(measureNanoTime {
            successful = true

            circuits.forEach {
                successful = successful && it.step(elapsed)
            }

            if(!successful){
                LOGGER.error("Failed to solve")
            }
        })

        subscribers.forEach {
            it.simulationTick(elapsed)
        }

        updates++

        simulationStopLock.unlock()
    }

    /**
     * This realizes the object subsets and creates the underlying simulations.
     * The simulation must be suspended before calling this method.
     * @see stopSimulation
     * */
    fun buildSolver() {
        validateMutationAccess()

        cells.forEach { it.clearObjectConnections() }
        cells.forEach { it.recordObjectConnections() }

        realizeElectrical()

        cells.forEach { it.build() }

        circuits.forEach { postProcessCircuit(it) }
    }

    /**
     * This method realizes the electrical circuits for all cells that have an electrical object.
     * */
    private fun realizeElectrical() {
        LOGGER.info("Realizing electrical components.")

        circuits.clear()

        realizeComponents(SimulationObjectType.Electrical) { set ->
            val circuit = Circuit()

            set.forEach { it.electricalObject.setNewCircuit(circuit) }

            circuits.add(circuit)

            LOGGER.info("Found circuit with ${circuit.components.size} components")
        }
    }

    /**
     * Realizes a subset of simulation objects that share the same simulation type.
     * This is a group of objects that:
     *  1. Are in cells that are connected (physically connected)
     *  2. Participate in the same simulation type (Electrical, Thermal, Mechanical)
     *
     * A separate solver/simulator may be created using this subset.
     *
     * This algorithm first creates a set with all cells that have the specified simulation type.
     * Then, it does a search through the cells, only taking into account connected nodes that have that simulation type.
     * When a cell is discovered, it is removed from the pending set.
     * At the end of the search, a connected component is realized.
     * The search is repeated until the pending set is exhausted.
     *
     * @param type The simulation type to search for.
     * @param factory A factory method to generate the subset from the discovered cells.
     * */
    private fun <TComponent> realizeComponents(
        type: SimulationObjectType,
        factory: ((HashSet<CellBase>) -> TComponent)
    ) {

        val pending = HashSet(cells.filter { it.hasObject(type) })
        val queue = ArrayDeque<CellBase>()

        // todo: can we use pending instead?
        val visited = HashSet<CellBase>()

        val results = ArrayList<TComponent>()

        while (pending.size > 0) {
            assert(queue.size == 0)

            visited.clear()

            queue.add(pending.first())

            while (queue.size > 0) {
                val cell = queue.removeFirst()

                if (!visited.add(cell)) {
                    continue
                }

                pending.remove(cell)

                cell.connections.forEach { connectedCellInfo ->
                    if (connectedCellInfo.cell.hasObject(type)) {
                        queue.add(connectedCellInfo.cell)
                    }
                }
            }

            results.add(factory(visited))
        }
    }

    private fun postProcessCircuit(circuit: Circuit){
        if(circuit.isFloating){
            fixFloating(circuit)
        }
    }

    private fun fixFloating(circuit: Circuit){
        var found = false
        for (comp in circuit.components) {
            if (comp is VoltageSource) {
                dprintln("F.<init>: floating: ground $comp pin 1 (node ${comp.node(1)}")
                comp.ground(1)
                found = true
                break
            }
        }
        if (!found) println("WARN: F.<init>: floating circuit and no VSource; the matrix is likely underconstrained.")
    }

    /**
     * Gets the cell at the specified CellPos.
     * @return The cell, if found, or throws an exception, if the cell does not exist.
     * */
    fun getCell(pos: CellPos): CellBase {
        val result = posCells[pos]

        if (result == null) {
            LOGGER.error("Could not get cell at $pos")
            error("")
        }

        return result
    }

    /**
     * Removes a cell from the internal sets, and invalidates the saved data.
     * **This does not update the solver!
     * It is assumed that multiple operations of this type will be performed, then,
     * the solver update will occur explicitly.**
     * The simulation must be stopped before calling this.
     * */
    fun removeCell(cell: CellBase) {
        validateMutationAccess()

        cells.remove(cell)
        posCells.remove(cell.pos)
        manager.setDirty()
    }

    /**
     * Adds a cell to the internal sets, assigns its graph, and invalidates the saved data.
     * **This does not update the solver!
     * It is assumed that multiple operations of this type will be performed, then,
     * the solver update will occur explicitly.**
     * The simulation must be stopped before calling this.
     * */
    fun addCell(cell: CellBase) {
        validateMutationAccess()

        cells.add(cell)
        cell.graph = this
        posCells[cell.pos] = cell
        manager.setDirty()
    }

    /**
     * Copies the cells of this graph to the other graph, and invalidates the saved data.
     * The simulation must be stopped before calling this.
     * */
    fun copyTo(graph: CellGraph) {
        validateMutationAccess()

        graph.cells.addAll(cells)

        manager.setDirty()
    }

    /**
     * Removes the graph from tracking and invalidates the saved data.
     * The simulation must be stopped before calling this.
     * */
    fun destroy() {
        validateMutationAccess()

        manager.removeGraph(this)
        manager.setDirty()
    }

    /**
     * Stops the simulation. This is a sync point, so usage of this should be sparse.
     * Will result in an error if it was not running.
     * */
    fun stopSimulation() {
        if (runningTask == null) {
            error("Tried to stop simulation, but it was not running")
        }

        simulationStopLock.lock()
        runningTask!!.cancel(true)
        runningTask = null
        simulationStopLock.unlock()

        LOGGER.info("Stopped simulation for $this")
    }

    /**
     * Starts the simulation. Will result in an error if it is already running.,
     * */
    fun startSimulation() {
        if (runningTask != null) {
            error("Tried to start simulation, but it was already running")
        }

        runningTask = pool.scheduleAtFixedRate(this::update, 0, 10, TimeUnit.MILLISECONDS)

        LOGGER.info("Started simulation for $this")
    }

    fun toNbt(): CompoundTag {
        stopSimulation()

        val circuitCompound = CompoundTag()
        circuitCompound.putUUID("ID", id)

        val cellListTag = ListTag()

        cells.forEach { cell ->
            val cellTag = CompoundTag()
            val connectionsTag = ListTag()

            cell.connections.forEach { connectionInfo ->
                val connectionCompound = CompoundTag()
                connectionCompound.putCellPos("Position", connectionInfo.cell.pos)
                connectionCompound.putRelativeDirection("Direction", connectionInfo.sourceDirection)
                connectionsTag.add(connectionCompound)
            }

            cellTag.putCellPos("Position", cell.pos)
            cellTag.putString("ID", cell.id.toString())
            cellTag.put("Connections", connectionsTag)

            cellListTag.add(cellTag)
        }

        circuitCompound.put("Cells", cellListTag)

        startSimulation()

        return circuitCompound
    }

    private data class ConnectionInfoCell(val cellPos: CellPos, val direction: RelativeRotationDirection)

    companion object {
        init {
            // We do get an exception from thread pool creation, but explicit handling is better here.
            if (Configuration.config.simulationThreads == 0) {
                error("Simulation threads is 0")
            }

            LOGGER.info("Using ${Configuration.config.simulationThreads} simulation threads")
        }

        private val pool = Executors.newScheduledThreadPool(Configuration.config.simulationThreads)

        fun fromNbt(graphCompound: CompoundTag, manager: CellGraphManager): CellGraph {
            val id = graphCompound.getUUID("ID")
            val result = CellGraph(id, manager)
            val cellListTag = graphCompound.get("Cells") as ListTag?
                ?: // no cells are to be loaded
                return result

            // used to assign the connections after all cells have been loaded
            val cellConnections = HashMap<CellBase, ArrayList<ConnectionInfoCell>>()

            cellListTag.forEach { cellNbt ->
                val cellCompound = cellNbt as CompoundTag
                val pos = cellCompound.getCellPos("Position")
                val cellId = ResourceLocation.tryParse(cellCompound.getString("ID"))!!

                val connectionPositions = ArrayList<ConnectionInfoCell>()
                val connectionsTag = cellCompound.get("Connections") as ListTag

                connectionsTag.forEach {
                    val connectionCompound = it as CompoundTag
                    val connectionPos = connectionCompound.getCellPos("Position")
                    val connectionDirection = connectionCompound.getRelativeDirection("Direction")
                    connectionPositions.add(ConnectionInfoCell(connectionPos, connectionDirection))
                }

                val cell = CellRegistry.getProvider(cellId).create(pos)

                cellConnections[cell] = connectionPositions

                result.addCell(cell)
            }

            // now assign all connections and the graph

            cellConnections.forEach { connectionEntry ->
                val cell = connectionEntry.component1()
                val connectionPositions = connectionEntry.component2()

                val connections = ArrayList(connectionPositions.map {
                    CellConnectionInfo(result.getCell(it.cellPos), it.direction)
                })

                // now set graph and connection
                cell.graph = result
                cell.connections = connections
                cell.update(connectionsChanged = true, graphChanged = true)
                cell.onLoadedFromDisk()
            }

            result.cells.forEach { it.onCreated() }

            return result
        }
    }
}
