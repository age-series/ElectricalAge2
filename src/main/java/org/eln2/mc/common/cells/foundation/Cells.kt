package org.eln2.mc.common.cells.foundation

import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.saveddata.SavedData
import net.minecraftforge.registries.ForgeRegistryEntry
import net.minecraftforge.server.ServerLifecycleHooks
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.VoltageSource
import org.ageseries.libage.sim.thermal.Simulator
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.CrossThreadAccess
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.common.configs.Configuration
import org.eln2.mc.common.space.*
import org.eln2.mc.data.DataNode
import org.eln2.mc.data.DataEntity
import org.eln2.mc.data.DataFieldMap
import org.eln2.mc.extensions.*
import org.eln2.mc.integration.WailaEntity
import org.eln2.mc.integration.WailaTooltipBuilder
import org.eln2.mc.sim.BiomeEnvironments
import org.eln2.mc.utility.Stopwatch
import org.eln2.mc.utility.Time
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.ArrayDeque
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField
import kotlin.system.measureNanoTime

data class CellPos(val descriptor: LocationDescriptor)

class TrackedSubscriberCollection(val underlying: SubscriberCollection) : SubscriberCollection {
    private val subscribers = HashSet<Sub>()

    override fun addSubscriber(parameters: SubscriberOptions, subscriber: Subscriber) {
        require(subscribers.add(Sub(parameters, subscriber))) { "Duplicate subscriber $subscriber" }
        underlying.addSubscriber(parameters, subscriber)
    }

    override fun remove(subscriber: Subscriber) {
        require(subscribers.removeAll { it.subscriber == subscriber }) { "Subscriber $subscriber was never added" }
        underlying.remove(subscriber)
    }

    fun destroy() {
        subscribers.forEach { underlying.remove(it.subscriber) }
        subscribers.clear()
    }

    private data class Sub(val parameters: SubscriberOptions, val subscriber: Subscriber)
}

data class CellCI(
    val pos: CellPos,
    val id: ResourceLocation,
    val envFm: DataFieldMap
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class SimObject

/**
 * The cell is a physical unit, that may participate in multiple simulations. Each simulation will
 * have a Simulation Object associated with it.
 * Cells create connections with other cells, and objects create connections with other objects of the same simulation type.
 * */
abstract class Cell(val pos: CellPos, val id: ResourceLocation, val envFm: DataFieldMap) : WailaEntity, DataEntity {
    constructor(ci: CellCI) : this(ci.pos, ci.id, ci.envFm)

    private fun interface ObjAccessor {
        fun get(inst: Cell): Any?
    }

    companion object {
        private val OBJ_SET_LOADERS = ConcurrentHashMap<Class<*>, List<ObjAccessor>>()
        private const val CELL_DATA = "cellData"
        private const val OBJECT_DATA = "objectData"

        private fun streamObjectLoaders(k: Class<Cell>) =
            OBJ_SET_LOADERS.getOrPut(k) {
                val accessors = mutableListOf<ObjAccessor>()

                k.kotlin
                    .memberProperties
                    .filter { it.javaField?.isAnnotationPresent(SimObject::class.java) ?: false }
                    .forEach {
                        if(!(it.returnType.classifier as KClass<*>).isSubclassOf(SimulationObject::class)) {
                            error("Invalid simulation field $it")
                        }

                        accessors.add(it::get)
                    }

                accessors
            }
    }

    val posDescr get() = pos.descriptor

    private var trackedPool: TrackedSubscriberCollection? = null
    val subscribers: SubscriberCollection get() = trackedPool
        ?: error("Failed to fetch subscriber collection")

    lateinit var graph: CellGraph
    lateinit var connections: ArrayList<Cell>

    private val ruleSetLazy = lazy { LocatorRelationRuleSet() }
    protected val ruleSet get() = ruleSetLazy.value

    open fun acceptsConnection(remote: Cell): Boolean {
        return ruleSet.accepts(pos.descriptor, remote.pos.descriptor)
    }

    /**
     * If [hasGraph], [CellGraph.setChanged] is called to ensure the cell data will be saved.
     * */
    fun setChanged(){
        if(hasGraph){
            graph.setChanged()
        }
    }

    val hasGraph get() = this::graph.isInitialized

    fun removeConnection(cell: Cell) {
        if(!connections.remove(cell)) {
            error("Tried to remove non-existent connection")
        }
    }

    var container: CellContainer? = null

    private val objSetLazy = lazy {
        createObjSet().also { set ->
            set.process { obj ->
                if(obj is DataEntity) {
                    dataNode.withChild(obj.dataNode)
                }
            }
        }
    }

    private val behaviorsLazy = lazy { CellBehaviorContainer(this) }

    protected val behaviorsInitialized get() = behaviorsLazy.isInitialized()

    protected val behaviors get() = behaviorsLazy.value

    /**
     * Called once when the object set is requested. The result is then cached.
     * @return A new object set, with all the desired objects.
     * */
    open fun createObjSet() = SimulationObjectSet(
        streamObjectLoaders(this.javaClass).mapNotNull {
            it.get(this) as? SimulationObject
        }
    )

    val objSet: SimulationObjectSet get() = objSetLazy.value

    fun loadTag(tag: CompoundTag) {
        tag.useSubTagIfPreset(CELL_DATA, this::loadCellData)
        tag.useSubTagIfPreset(OBJECT_DATA, this::loadObjectData)
    }

    /**
     * Called after all graphs in the level have been loaded, before the solver is built.
     * */
    open fun onWorldLoadedPreSolver() { }
    /**
     * Called after all graphs in the level have been loaded, after the solver is built.
     */
    open fun onWorldLoadedPostSolver() { }

    /**
     * Called after all graphs in the level have been loaded, before the simulations start.
     * */
    open fun onWorldLoadedPreSim() { }

    /**
     * Called after all graphs in the level have been loaded, after the simulations have started.
     * */
    open fun onWorldLoadedPostSim() { }

    fun createTag(): CompoundTag {
        return CompoundTag().also { tag ->
            tag.apply {
                withSubTagOptional(CELL_DATA, saveCellData())
                putSubTag(OBJECT_DATA) { saveObjectData(it) }
            }
        }
    }

    /**
     * Called when the graph is being loaded. Custom data saved by [saveCellData] will be passed here.
     * */
    open fun loadCellData(tag: CompoundTag) { }

    /**
     * Called when the graph is being saved. Custom data should be saved here.
     * */
    open fun saveCellData(): CompoundTag? = null

    private fun saveObjectData(tag: CompoundTag) {
        objSet.process { obj ->
            if(obj is PersistentObject) {
                tag.put(obj.type.name, obj.save())
            }
        }
    }

    private fun loadObjectData(tag: CompoundTag) {
        objSet.process { obj ->
            if(obj is PersistentObject) {
                obj.load(tag.getCompound(obj.type.name))
            }
        }
    }

    /**
     * Called when the tile entity is being unloaded.
     * After this method is called, the field will become null.
     */
    open fun onContainerUnloaded() {}

    /**
     * Called when the block entity is being loaded.
     * The field is assigned before this is called.
     */
    open fun onContainerLoaded() {}

    /**
     * Called when the graph manager completed loading this cell from the disk.
     */
    open fun onLoadedFromDisk() {}

    fun create() {
        onCreated()
    }

    /**
     * Called after the cell was created.
     */
    protected open fun onCreated() {}

    fun remove() {
        behaviors.destroy()
        onRemoving()
        trackedPool?.destroy()
    }

    /**
     * Called while the cell is being destroyed, just after the simulation was stopped.
     * Subscribers may be cleaned up here.
     * */
    protected open fun onRemoving() {}

    fun destroy() {
        onDestroyed()
    }

    /**
     * Called after the cell was destroyed.
     */
    protected open fun onDestroyed() {
        objSet.process { it.destroy() }
    }

    /**
     * Called when the graph and/or neighbouring cells are updated. This method is called after completeDiskLoad and setPlaced
     * @param connectionsChanged True if the neighbouring cells changed.
     * @param graphChanged True if the graph that owns this cell has been updated.
     */
    open fun update(connectionsChanged: Boolean, graphChanged: Boolean) {
        if(connectionsChanged) {
            onConnectionsChanged()
        }

        if(graphChanged) {
            trackedPool?.destroy()
            trackedPool = TrackedSubscriberCollection(graph.subscribers)
            behaviors.changeGraph()
            onGraphChanged()
            subscribe(subscribers)
        }
    }

    /**
     * Called when this cell's connection list changes.
     * */
    protected open fun onConnectionsChanged() {}

    /**
     * Called when this cell joined another graph.
     * */
    protected open fun onGraphChanged() {}

    protected open fun subscribe(subs: SubscriberCollection) { }

    /**
     * Called when the solver is being built, in order to clear and prepare the objects.
     * */
    fun clearObjectConnections() {
        objSet.process { it.clear() }
    }

    /**
     * Called when the solver is being built, in order to record all object-object connections.
     * */
    fun recordObjectConnections() {
        objSet.process { localObj ->
            connections.forEach { remoteCell ->
                if(!localObj.acceptsRemote(remoteCell.pos.descriptor)) {
                    return@forEach
                }

                if (!remoteCell.hasObject(localObj.type)) {
                    return@forEach
                }

                val remoteObj = remoteCell.objSet[localObj.type]

                require(remoteCell.connections.contains(this)) { "Mismatched connection set" }

                if(!remoteObj.acceptsRemote(pos.descriptor)) {
                    return@forEach
                }

                // We can form a connection here.

                when (localObj.type) {
                    SimulationObjectType.Electrical -> {
                        (localObj as ElectricalObject).addConnection(
                            remoteCell.objSet.electricalObject
                        )
                    }

                    SimulationObjectType.Thermal -> {
                        (localObj as ThermalObject).addConnection(
                            remoteCell.objSet.thermalObject
                        )
                    }
                }
            }
        }
    }

    /**
     * Called when the solver is being built, in order to finish setting up the underlying components in the
     * simulation objects.
     * */
    fun build() {
        objSet.process { it.build() }
    }

    /**
     * Checks if this cell has the specified simulation object type.
     * @return True if this cell has the required object. Otherwise, false.
     * */
    fun hasObject(type: SimulationObjectType): Boolean {
        return objSet.hasObject(type)
    }

    /**
     * By default, the cell just passes down the call to objects that implement the WAILA provider.
     * */
    override fun appendBody(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        if (hasGraph) {
            builder.text("Graph", graph.id)
        }

        objSet.process {
            if (it is WailaEntity) {
                it.appendBody(builder, config)
            }
        }

        behaviors.forEach {
            if(it is WailaEntity) {
                it.appendBody(builder, config)
            }
        }
    }

    override val dataNode: DataNode = DataNode()
}

/**
 * [CellConnections] has all Cell-Cell connection logic and is responsible for building *physical* networks.
 * There are two key algorithms here:
 * - Cell Insertion
 *      - Inserts a cell into the world, and may form connections with other cells.
 *
 * - Cell Deletion
 *      - Deletes a cell from the world, and may result in many topological changes to the associated graph.
 *        An example would be the removal (deletion) of a cut vertex. This would result in the graph splintering into multiple disjoint graphs.
 *        This is the most intensive part of the algorithm. It may be optimized (the algorithm implemented here is certainly suboptimal),
 *        but it has been determined that this is not a cause for concern,
 *        as it only represents a small slice of the performance impact caused by network updates.
 *
 *
 * @see <a href="https://en.wikipedia.org/wiki/Biconnected_component">Wikipedia - Bi-connected component</a>
 * */
object CellConnections {
    /**
     * Inserts a cell into a graph. It may create connections with other cells, and cause
     * topological changes to related networks.
     * */
    fun insert(container: CellContainer, cell: Cell) {
        connectCell(cell, container)
        cell.create()
    }

    /**
     * Removes a cell from the graph. It may cause topological changes to the graph, as outlined in the top document.
     * */
    fun delete(cellInfo: Cell, container: CellContainer) {
        disconnectCell(cellInfo, container)
        cellInfo.destroy()
    }

    private fun connectCell(insertedCell: Cell, container: CellContainer) {
        val manager = container.manager
        val neighborInfoList = container.neighborScan(insertedCell)
        val neighborCells = neighborInfoList.map { it.neighbor }.toHashSet()

        // Stop all running simulations

        neighborCells.map { it.graph }.distinct().forEach {
            it.ensureStopped()
        }

        /*
        * Cases:
        *   1. We don't have any neighbors. We must create a new circuit.
        *   2. We have a single neighbor. We can add this cell to their circuit.
        *   3. We have multiple neighbors, but they are part of the same circuit. We can add this cell to the common circuit.
        *   4. We have multiple neighbors, and they are part of different circuits. We need to create a new circuit,
        *       that contains the cells of the other circuits, plus this one.
        * */

        // This is common logic for all cases

        insertedCell.connections = ArrayList(neighborInfoList.map { it.neighbor })

        neighborInfoList.forEach { neighborInfo ->
            LOGGER.info("Neighbor $neighborInfo")

            neighborInfo.neighbor.connections.add(insertedCell)
            neighborInfo.neighborContainer.onCellConnected(
                neighborInfo.neighbor,
                insertedCell
            )

            container.onCellConnected(insertedCell, neighborInfo.neighbor)
        }

        if (neighborInfoList.isEmpty()) {
            // Case 1. Create new circuit

            val graph = manager.createGraph()

            graph.addCell(insertedCell)

            graph.setChanged()
        } else if (isCommonGraph(neighborInfoList)) {
            // Case 2 and 3. Join the existing circuit.

            val graph = neighborInfoList[0].neighbor.graph

            graph.addCell(insertedCell)

            graph.setChanged()

            // Send connection update to the neighbor (the graph has not changed):
            neighborInfoList.forEach {
                it.neighbor.update(
                    connectionsChanged = true,
                    graphChanged = false
                )
            }
        } else {
            // Case 4. We need to create a new circuit, with all cells and this one.

            // Identify separate graphs:
            val disjointGraphs = neighborInfoList.map { it.neighbor.graph }.distinct()

            // Create new graph that will eventually have all cells and the inserted one:
            val graph = manager.createGraph()

            // Register inserted cell:
            graph.addCell(insertedCell)

            // Copy cells over to the new circuit and destroy previous circuits:
            disjointGraphs.forEach { existingGraph ->
                existingGraph.copyTo(graph)

                /*
                * We also need to refit the existing cells.
                * Connections of the remote cells have changed only if the remote cell is a neighbor of the inserted cell.
                * This is because inserting a cell cannot remove connections, and new connections appear only between the new cell and cells from other circuits (the inserted cell is a cut vertex)
                * */
                existingGraph.cells.forEach { cell ->
                    cell.graph = graph

                    cell.update(
                        connectionsChanged = neighborCells.contains(cell), // As per the above explanation
                        graphChanged = true // We are destroying the old graph and copying, so this is true
                    )

                    cell.container?.onTopologyChanged()
                }

                // And now destroy the old graph:
                existingGraph.destroy()
            }

            graph.setChanged()
        }

        insertedCell.graph.buildSolver()

        /*
        * The inserted cell had a "complete" update.
        * Because it was inserted into a new network, its neighbors have changed (connectionsChanged is true).
        * Then, because it is inserted into a new graph, graphChanged is also true:
        * */
        insertedCell.update(connectionsChanged = true, graphChanged = true)
        insertedCell.container?.onTopologyChanged()

        // And now resume/start the simulation:
        insertedCell.graph.startSimulation()
    }

    private fun disconnectCell(cell: Cell, container: CellContainer) {
        val manager = container.manager
        val neighborCells = container.neighborScan(cell)

        val graph = cell.graph

        // Stop Simulation
        graph.stopSimulation()

        cell.remove()

        // This is common logic for all cases.
        neighborCells.forEach { (neighbor, neighborContainer) ->
            neighbor.removeConnection(cell)
            neighborContainer.onCellDisconnected(neighbor, cell)
        }

        /*
        *   Cases:
        *   1. We don't have any neighbors. We can destroy the circuit.
        *   2. We have a single neighbor. We can remove ourselves from the circuit.
        *   3. We have multiple neighbors, and we are not a cut vertex. We can remove ourselves from the circuit.
        *   4. We have multiple neighbors, and we are a cut vertex. We need to remove ourselves, find the new disjoint graphs,
        *        and rebuild the circuits.
        */

        if (neighborCells.isEmpty()) {
            // Case 1. Destroy this circuit.

            // Make sure we don't make any logic errors somewhere else.
            assert(graph.cells.size == 1)

            graph.destroy()
        } else if (neighborCells.size == 1) {
            // Case 2.

            // Remove the cell from the circuit.
            graph.removeCell(cell)

            val neighbor = neighborCells[0].neighbor

            neighbor.update(connectionsChanged = true, graphChanged = false)

            graph.buildSolver()
            graph.startSimulation()
            graph.setChanged()
        } else {
            // Case 3 and 4. Implement a more sophisticated algorithm, if necessary.
            graph.destroy()
            rebuildTopologies(neighborCells, cell, manager)
        }
    }

    /**
     * Checks whether the cells share the same graph.
     * @return True, if the specified cells share the same graph. Otherwise, false.
     * */
    private fun isCommonGraph(neighbors: ArrayList<CellNeighborInfo>): Boolean {
        if (neighbors.size < 2) {
            return true
        }

        val graph = neighbors[0].neighbor.graph

        neighbors.drop(1).forEach { info ->
            if (info.neighbor.graph != graph) {
                return false
            }
        }

        return true
    }

    /**
     * Rebuilds the topology of a graph, presumably after a cell has been removed.
     * This will handle cases such as the graph splitting, because a cut vertex was removed.
     * This is a performance intensive operation, because it is likely to perform a search through the cells.
     * There is a case, though, that will complete in constant time: removing a cell that has zero or one neighbors.
     * Keep in mind that the simulation logic likely won't complete in constant time, in any case.
     * */
    private fun rebuildTopologies(
        neighborInfoList: ArrayList<CellNeighborInfo>,
        removedCell: Cell,
        manager: CellGraphManager
    ) {
        /*
        * For now, we use this simple algorithm.:
        *   We enqueue all neighbors for visitation. We perform searches through their graphs,
        *   excluding the cell we are removing.
        *
        *   If at any point we encounter an unprocessed neighbor, we remove that neighbor from the neighbor
        *   queue.
        *
        *   After a queue element has been processed, we build a new circuit with the cells we found.
        * */

        val neighbors = neighborInfoList.map { it.neighbor }.toHashSet()
        val neighborQueue = ArrayDeque<Cell>()
        neighborQueue.addAll(neighbors)

        val bfsVisited = HashSet<Cell>()
        val bfsQueue = ArrayDeque<Cell>()

        while (neighborQueue.size > 0) {
            val neighbor = neighborQueue.removeFirst()

            // Create new circuit for all cells connected to this one.
            val graph = manager.createGraph()

            // Start BFS at the neighbor.
            bfsQueue.add(neighbor)

            while (bfsQueue.size > 0) {
                val cell = bfsQueue.removeFirst()

                if (!bfsVisited.add(cell)) {
                    continue
                }

                // Remove it from the neighbor queue, if it exists.
                // todo: can we add an exit condition here?
                // Hypothesis: If at any point, the neighbor queue becomes empty, we can stop traversal, and use the cells
                // in the old circuit, minus the one we are removing. This helps performance if there are close
                // cycles around the cell we are removing.
                neighborQueue.remove(cell)

                graph.addCell(cell)

                // Enqueue neighbors (excluding the cell we are removing) for processing
                cell.connections.forEach { connCell ->
                    // This must be handled above.
                    assert(connCell != removedCell)

                    bfsQueue.add(connCell)
                }
            }

            assert(bfsQueue.isEmpty())

            // Refit cells
            graph.cells.forEach { cell ->
                val isNeighbor = neighbors.contains(cell)

                cell.update(connectionsChanged = isNeighbor, graphChanged = true)
                cell.container?.onTopologyChanged()
            }

            // Finally, build the solver and start simulation.

            graph.buildSolver()
            graph.startSimulation()
            graph.setChanged()

            // We don't need to keep the cells, we have already traversed all the connected ones.
            bfsVisited.clear()
        }
    }
}

fun planarCellScan(level: Level, actualCell: Cell, searchDirection: Direction, consumer: ((CellNeighborInfo) -> Unit)) {
    val actualPosWorld = actualCell.posDescr.requireLocator<R3, BlockPosLocator> { "Planar Scan requires a block position" }.pos
    val actualFaceTarget = actualCell.posDescr.requireLocator<SO3, BlockFaceLocator> { "Planar Scan requires a face" }.faceWorld
    val remoteContainer = level.getBlockEntity(actualPosWorld + searchDirection) as? CellContainer ?: return

    remoteContainer
        .getCells()
        .filter {
            // Select cells that we can search using this algorithm. Those cells are SE(3) parameterized, so we can search using the position and face rotation.
            val desc = it.pos.descriptor

            desc.hasLocator<R3, BlockPosLocator>() && desc.hasLocator<SO3, BlockFaceLocator>()
        }
        .forEach { targetCell ->
            val targetFaceTarget = targetCell.pos.descriptor.requireLocator<SO3, BlockFaceLocator>().faceWorld

            if(targetFaceTarget == actualFaceTarget) {
                if(actualCell.acceptsConnection(targetCell) && targetCell.acceptsConnection(actualCell)){
                    consumer(CellNeighborInfo(targetCell, remoteContainer))
                }
            }
        }
}

fun wrappedCellScan(level: Level, actualCell: Cell, searchDirectionTarget: Direction, consumer: ((CellNeighborInfo) -> Unit)){
    val actualPosWorld = actualCell.pos.descriptor.requireLocator<R3, BlockPosLocator> { "Wrapped Scan requires a block position" }.pos
    val actualFaceActual = actualCell.pos.descriptor.requireLocator<SO3, BlockFaceLocator> { "Wrapped Scan requires a face" }.faceWorld
    val wrapDirection = actualFaceActual.opposite
    val remoteContainer = level.getBlockEntity(actualPosWorld + searchDirectionTarget + wrapDirection) as? CellContainer
        ?: return

    remoteContainer
        .getCells()
        .filter {
            // Select cells that we can search using this algorithm. Those cells are SE(3) parameterized, so we can search using the position and face rotation.
            val desc = it.pos.descriptor
            desc.hasLocator<R3, BlockPosLocator>() && desc.hasLocator<SO3, BlockFaceLocator>()
        }
        .forEach { targetCell ->
            val targetFaceTarget = targetCell.pos.descriptor.requireLocator<SO3, BlockFaceLocator>().faceWorld

            if (targetFaceTarget == searchDirectionTarget) {
                if (actualCell.acceptsConnection(targetCell) && targetCell.acceptsConnection(actualCell)) {
                    consumer(CellNeighborInfo(targetCell, remoteContainer))
                }
            }
        }
}

interface CellContainer {
    fun getCells(): ArrayList<Cell>
    fun neighborScan(actualCell: Cell): ArrayList<CellNeighborInfo>
    fun onCellConnected(actualCell: Cell, remoteCell: Cell)
    fun onCellDisconnected(actualCell: Cell, remoteCell: Cell)
    fun onTopologyChanged()

    val manager: CellGraphManager
}

/**
 * Encapsulates information about a neighbor cell.
 * */
data class CellNeighborInfo(val neighbor: Cell, val neighborContainer: CellContainer)

/**
 * The cell graph represents a physical network of cells.
 * It may have multiple simulation subsets, formed between objects in the cells of this graph.
 * The cell graph manages the solver and simulation.
 * It also has serialization/deserialization logic for saving to the disk using NBT.
 * */
class CellGraph(val id: UUID, val manager: CellGraphManager, val level: ServerLevel) {
    val cells = ArrayList<Cell>()

    private val posCells = HashMap<CellPos, Cell>()

    private val electricalSims = ArrayList<Circuit>()
    private val thermalSims = ArrayList<Simulator>()

    private val simStopLock = ReentrantLock()

    // This is the simulation task. It will be null if the simulation is stopped
    private var simTask: ScheduledFuture<*>? = null

    val isSimRunning get() = simTask != null

    @CrossThreadAccess
    private var updates = 0L

    private var updatesCheckpoint = 0L

    val subscribers = SubscriberPool()

    @CrossThreadAccess
    var lastTickTime = 0.0
        private set

    fun setChanged(){
        manager.setDirty()
    }

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
    var isElectricalSuccessful = false
        private set

    /**
     * Checks if the simulation is running. Presumably, this is used by logic that wants to mutate the graph.
     * It also checks if the caller is the server thread.
     * */
    private fun validateMutationAccess() {
        if (simTask != null) {
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
        simStopLock.lock()

        val elapsed = 1.0 / 100.0

        subscribers.update(elapsed, SubscriberPhase.Pre)

        lastTickTime = Time.toSeconds(measureNanoTime {
            isElectricalSuccessful = true

            electricalSims.forEach {
                val success = it.step(elapsed)

                isElectricalSuccessful = isElectricalSuccessful && success

                if (!success && !it.isFloating) {
                    LOGGER.error("Failed to update non-floating circuit!")
                }
            }

            thermalSims.forEach {
                it.step(elapsed)
            }
        })

        subscribers.update(elapsed, SubscriberPhase.Post)

        updates++

        simStopLock.unlock()
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
        realizeThermal()

        cells.forEach { it.build() }
        electricalSims.forEach { postProcessCircuit(it) }
    }

    /**
     * This method realizes the electrical circuits for all cells that have an electrical object.
     * */
    private fun realizeElectrical() {
        electricalSims.clear()

        realizeComponents(SimulationObjectType.Electrical) { set ->
            val circuit = Circuit()
            set.forEach { it.objSet.electricalObject.setNewCircuit(circuit) }
            electricalSims.add(circuit)
        }
    }

    private fun realizeThermal() {
        thermalSims.clear()

        realizeComponents(SimulationObjectType.Thermal) { set ->
            val simulation = Simulator()
            set.forEach { it.objSet.thermalObject.setNewSimulation(simulation) }
            thermalSims.add(simulation)
        }
    }

    /**
     * Realizes a subset of simulation objects that share the same simulation type.
     * This is a group of objects that:
     *  1. Are in cells that are physically connected
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
    private fun <TComponent> realizeComponents(type: SimulationObjectType, factory: ((java.util.HashSet<Cell>) -> TComponent)) {
        val pending = HashSet(cells.filter { it.hasObject(type) })
        val queue = ArrayDeque<Cell>()

        // todo: can we use pending instead?
        val visited = java.util.HashSet<Cell>()

        val results = java.util.ArrayList<TComponent>()

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

                cell.connections.forEach { connectedCell ->
                    if (connectedCell.hasObject(type)) {
                        queue.add(connectedCell)
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
                comp.ground(1)
                found = true
                break
            }
        }
        if (!found) {
            LOGGER.warn("Floating circuit and no VSource; the matrix is likely under-constrained.")
        }
    }

    /**
     * Gets the cell at the specified CellPos.
     * @return The cell, if found, or throws an exception, if the cell does not exist.
     * */
    fun getCell(pos: CellPos): Cell {
        val result = posCells[pos]

        if (result == null) {
            LOGGER.error("Could not get cell at $pos") // exception may be swallowed
            error("Could not get cell at $pos")
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
    fun removeCell(cell: Cell) {
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
    fun addCell(cell: Cell) {
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

    fun ensureStopped() {
        if(isSimRunning) {
            stopSimulation()
        }
    }

    /**
     * Stops the simulation. This is a sync point, so usage of this should be sparse.
     * Will result in an error if it was not running.
     * */
    fun stopSimulation() {
        if (simTask == null) {
            error("Tried to stop simulation, but it was not running")
        }

        simStopLock.lock()
        simTask!!.cancel(true)
        simTask = null
        simStopLock.unlock()

        LOGGER.info("Stopped simulation for $this")
    }

    /**
     * Starts the simulation. Will result in an error if it is already running.,
     * */
    fun startSimulation() {
        if (simTask != null) {
            error("Tried to start simulation, but it was already running")
        }

        simTask = pool.scheduleAtFixedRate(this::update, 0, 10, TimeUnit.MILLISECONDS)

        LOGGER.info("Started simulation for $this")
    }

    /**
     * Runs the specified [action], ensuring that the simulation is paused.
     * The previous running state is preserved; if the simulation was paused, it will not be started after the [action] is completed.
     * If it was running, then the simulation will resume.
     * */
    fun runSuspended(action: (() -> Unit)) {
        val running = isSimRunning

        if (running){
            stopSimulation()
        }

        action()

        if(running) {
            startSimulation()
        }
    }

    fun toNbt(): CompoundTag {
        val circuitCompound = CompoundTag()

        runSuspended {
            circuitCompound.putUUID(ID, id)

            val cellListTag = ListTag()

            cells.forEach { cell ->
                val cellTag = CompoundTag()
                val connectionsTag = ListTag()

                cell.connections.forEach { conn ->
                    val connectionCompound = CompoundTag()
                    connectionCompound.putCellPos(POSITION, conn.pos)
                    connectionsTag.add(connectionCompound)
                }

                cellTag.putCellPos(POSITION, cell.pos)
                cellTag.putString(ID, cell.id.toString())
                cellTag.put(CONNECTIONS, connectionsTag)

                try{
                    cellTag.put(CELL_DATA, cell.createTag())
                }
                catch (t: Throwable) {
                    LOGGER.error("Cell save error: $t")
                }

                cellListTag.add(cellTag)
            }

            circuitCompound.put(CELLS, cellListTag)
        }

        return circuitCompound
    }

    fun serverStop(){
        if(simTask != null) {
            stopSimulation()
        }
    }

    companion object {
        private const val CELL_DATA = "data"
        private const val ID = "id"
        private const val CELLS = "cells"
        private const val POSITION = "pos"
        private const val CONNECTIONS = "connections"

        init {
            // We do get an exception from thread pool creation, but explicit handling is better here.
            if (Configuration.config.simulationThreads == 0) {
                error("Simulation threads is 0")
            }

            LOGGER.info("Using ${Configuration.config.simulationThreads} simulation threads")
        }

        private val threadNumber = AtomicInteger()

        private fun createThread(r: Runnable): Thread{
            val t = Thread(r, "cell-graph-${threadNumber.getAndIncrement()}")

            if (t.isDaemon){
                t.isDaemon = false
            }

            if (t.priority != Thread.NORM_PRIORITY){
                t.priority = Thread.NORM_PRIORITY
            }

            return t
        }

        private val pool = Executors.newScheduledThreadPool(
            Configuration.config.simulationThreads, ::createThread
        )

        fun fromNbt(graphCompound: CompoundTag, manager: CellGraphManager, level: ServerLevel): CellGraph {
            val id = graphCompound.getUUID(ID)
            val result = CellGraph(id, manager, level)

            val cellListTag = graphCompound.get(CELLS) as ListTag?
                ?: // No cells are available
                return result

            // Used to assign the connections after all cells have been loaded:
            val cellConnections = HashMap<Cell, java.util.ArrayList<CellPos>>()

            // Used to load cell custom data:
            val cellData = HashMap<Cell, CompoundTag>()

            cellListTag.forEach { cellNbt ->
                val cellCompound = cellNbt as CompoundTag
                val pos = cellCompound.getCellPos(POSITION)
                val cellId = ResourceLocation.tryParse(cellCompound.getString(ID))!!

                val connectionPositions = java.util.ArrayList<CellPos>()
                val connectionsTag = cellCompound.get(CONNECTIONS) as ListTag

                connectionsTag.forEach {
                    val connectionCompound = it as CompoundTag
                    val connectionPos = connectionCompound.getCellPos(POSITION)
                    connectionPositions.add(connectionPos)
                }

                val cell = CellRegistry.getProvider(cellId).create(
                    pos,
                    BiomeEnvironments.cellEnv(level, pos).fieldMap()
                )

                cellConnections[cell] = connectionPositions

                result.addCell(cell)

                cellData[cell] = cellCompound.getCompound(CELL_DATA)
            }

            // Now assign all connections and the graph to the cells:
            cellConnections.forEach { (cell, connectionPositions) ->
                val connections = java.util.ArrayList<Cell>(connectionPositions.size)

                connectionPositions.forEach { connections.add(result.getCell(it)) }

                // now set graph and connection
                cell.graph = result
                cell.connections = connections
                cell.update(connectionsChanged = true, graphChanged = true)

                try {
                    cell.loadTag(cellData[cell]!!)
                }
                catch (t: Throwable) {
                    LOGGER.error("Cell loading exception: $t")
                }

                cell.onLoadedFromDisk()
            }

            result.cells.forEach { it.create() }

            return result
        }
    }
}

fun runSuspended(graphs: List<CellGraph>, action: () -> Unit) {
    if(graphs.isEmpty()) {
        action()

        return
    }

    graphs.first().runSuspended {
        runSuspended(graphs.drop(1), action)
    }
}

fun runSuspended(vararg graphs: CellGraph, action: () -> Unit) {
    runSuspended(graphs.asList(), action)
}

fun runSuspended(vararg cells: Cell, action: () -> Unit) {
    runSuspended(cells.asList().map { it.graph }, action)
}

/**
 * The Cell Graph Manager tracks the cell graphs for a single dimension.
 * This is a **server-only** construct. Simulations never have to occur on the client.
 * */
class CellGraphManager(val level: ServerLevel) : SavedData() {
    private val graphs = HashMap<UUID, CellGraph>()

    private val statisticsWatch = Stopwatch()

    fun sampleTickRate(): Double {
        val elapsedSeconds = statisticsWatch.sample()

        return graphs.values.sumOf { it.sampleElapsedUpdates() } / elapsedSeconds
    }

    val totalSpentTime get() = graphs.values.sumOf { it.lastTickTime }

    /**
     * Checks whether this manager is tracking the specified graph.
     * @return True, if the graph is being tracked by this manager. Otherwise, false.
     * */
    fun contains(id: UUID): Boolean {
        return graphs.containsKey(id)
    }

    /**
     * Begins tracking a graph, and invalidates the saved data.
     * */
    fun addGraph(graph: CellGraph) {
        graphs[graph.id] = graph
        setDirty()
    }

    /**
     * Creates a fresh graph, starts tracking it, and invalidates the saved data.
     * */
    fun createGraph(): CellGraph {
        val graph = CellGraph(UUID.randomUUID(), this, level)
        addGraph(graph)
        setDirty()
        return graph
    }

    /**
     * Removes a graph, and invalidates the saved data.
     * **This does not call any _destroy_ methods on the graph!**
     * */
    fun removeGraph(graph: CellGraph) {
        graphs.remove(graph.id)
        LOGGER.info("Removed graph ${graph.id}!")
        setDirty()
    }

    override fun save(tag: CompoundTag): CompoundTag {
        val graphListTag = ListTag()

        graphs.values.forEach { graph ->
            graphListTag.add(graph.toNbt())
        }

        tag.put("Graphs", graphListTag)
        LOGGER.info("Wrote ${graphs.count()} graphs to disk.")
        return tag
    }

    /**
     * Gets the graph with the specified ID, or throws an exception.
     * */
    fun getGraph(id: UUID): CellGraph {
        return graphs[id] ?: error("Graph with id $id not found")
    }

    fun serverStop(){
        graphs.values.forEach { it.serverStop() }
    }

    companion object {
        private fun load(tag: CompoundTag, level: ServerLevel): CellGraphManager {
            val manager = CellGraphManager(level)

            val graphListTag = tag.get("Graphs") as ListTag?

            if (graphListTag == null) {
                LOGGER.info("No nodes to be loaded!")
                return manager
            }

            graphListTag.forEach { circuitNbt ->
                val graphCompound = circuitNbt as CompoundTag
                val graph = CellGraph.fromNbt(graphCompound, manager, level)
                if (graph.cells.isEmpty()) {
                    LOGGER.error("Loaded circuit with no cells!")
                    return@forEach
                }

                manager.addGraph(graph)

                LOGGER.info("Loaded ${graph.cells.size} cells for ${graph.id}!")
            }

            manager.graphs.values.forEach {
                it.cells.forEach { cell -> cell.onWorldLoadedPreSolver() }
            }

            manager.graphs.values.forEach { it.buildSolver() }

            manager.graphs.values.forEach {
                it.cells.forEach { cell -> cell.onWorldLoadedPostSolver() }
            }

            manager.graphs.values.forEach {
                it.cells.forEach { cell -> cell.onWorldLoadedPreSim() }
            }

            manager.graphs.values.forEach { it.startSimulation() }

            manager.graphs.values.forEach {
                it.cells.forEach { cell -> cell.onWorldLoadedPostSim() }
            }

            return manager
        }

        /**
         * Gets or creates a graph manager for the specified level.
         * */
        fun getFor(level: ServerLevel): CellGraphManager {
            return level.dataStorage.computeIfAbsent(
                { load(it, level) },
                { CellGraphManager(level) },
                "CellManager")

        }
    }
}

/**
 * The Cell Provider is a factory of cells, and also has connection rules for cells.
 * */
abstract class CellProvider : ForgeRegistryEntry<CellProvider>() {
    val id: ResourceLocation get() = this.registryName ?: error("ID not available in CellProvider")

    protected abstract fun createInstance(ci: CellCI): Cell

    /**
     * Creates a new Cell, at the specified position.
     * */
    fun create(pos: CellPos, envNode: DataFieldMap): Cell {
        return createInstance(
            CellCI(
                pos,
                id,
                envNode
            )
        )
    }
}

/**
 * The cell factory is used by Cell Providers, to instantiate Cells.
 * Usually, the constructor of the cell can be passed as factory.
 * */
fun interface CellFactory {
    fun create(ci: CellCI): Cell
}

class BasicCellProvider(private val factory: CellFactory) : CellProvider() {
    override fun createInstance(ci: CellCI): Cell {
        return factory.create(ci)
    }
}

/**
 * These are some convention constants.
 * */
object CellConvention {
    /**
     * Describes the pin exported to other Electrical Objects.
     * */
    const val EXTERNAL_PIN = 1
    const val POSITIVE_PIN = EXTERNAL_PIN

    /**
     * Describes the pin used internally by Electrical Objects.
     * */
    const val INTERNAL_PIN = 0
    const val NEGATIVE_PIN = INTERNAL_PIN
}
