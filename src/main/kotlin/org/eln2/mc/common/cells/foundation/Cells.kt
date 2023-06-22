package org.eln2.mc.common.cells.foundation

import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.saveddata.SavedData
import net.minecraftforge.server.ServerLifecycleHooks
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.VoltageSource
import org.ageseries.libage.sim.thermal.Simulator
import org.eln2.mc.*
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.data.*
import org.eln2.mc.integration.WailaEntity
import org.eln2.mc.mathematics.Vector3di
import org.eln2.mc.scientific.*
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.ArrayDeque

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

data class CellCreateInfo(
    val pos: CellPos,
    val id: ResourceLocation,
    val envFm: DataFieldMap,
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class SimObject

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class Behavior

/**
 * The cell is a physical unit, that may participate in multiple simulations. Each simulation will
 * have a Simulation Object associated with it.
 * Cells create connections with other cells, and objects create connections with other objects of the same simulation type.
 * */
abstract class Cell(val pos: CellPos, val id: ResourceLocation, val envFldMap: DataFieldMap) : WailaEntity, DataEntity {
    constructor(ci: CellCreateInfo) : this(ci.pos, ci.id, ci.envFm)

    override val dataNode = DataNode().also { root ->
        root.withChild {
            it.data.withField(
                ObjectField("ID") {
                    if (hasGraph) graph.id
                    else null
                }
            )
        }
    }

    companion object {
        private val OBJECT_READERS = ConcurrentHashMap<Class<*>, List<FieldReader<Cell>>>()
        private val BEHAVIOR_READERS = ConcurrentHashMap<Class<*>, List<FieldReader<Cell>>>()

        private const val CELL_DATA = "cellData"
        private const val OBJECT_DATA = "objectData"
    }

    val posDescr get() = pos.descriptor

    private var persistentPool: TrackedSubscriberCollection? = null
    private var transientPool: TrackedSubscriberCollection? = null

    lateinit var graph: CellGraph
    lateinit var connections: ArrayList<Cell>

    private val ruleSetLazy = lazy { LocatorRelationRuleSet() }
    protected val ruleSet get() = ruleSetLazy.value

    private val servicesLazy = lazy {
        ServiceCollection()
            .withSingleton { dataNode }
            .withSingleton { this }
            .withSingleton(this.javaClass) { this }
            .withSingleton { posDescr }
            .withExternalResolver { dataNode.data.read(it) }
            .also { registerServices(it) }
    }

    protected val services get() = servicesLazy.value

    protected open fun registerServices(services: ServiceCollection) {}

    protected inline fun <reified T> activate(vararg extraParams: Any): T = services.activate(extraParams.asList())

    open fun acceptsConnection(remote: Cell): Boolean {
        return ruleSet.accepts(pos.descriptor, remote.pos.descriptor)
    }

    private val replicators = ArrayList<ReplicatorBehavior>()

    /**
     * If [hasGraph], [CellGraph.setChanged] is called to ensure the cell data will be saved.
     * */
    fun setChanged() {
        if (hasGraph) {
            graph.setChanged()
        }
    }

    val hasGraph get() = this::graph.isInitialized

    fun removeConnection(cell: Cell) {
        if (!connections.remove(cell)) {
            error("Tried to remove non-existent connection")
        }
    }

    var container: CellContainer? = null

    private val objSetLazy = lazy {
        createObjectSet().also { set ->
            set.process { obj ->
                if (obj is DataEntity) {
                    dataNode.withChild(obj.dataNode)
                }
            }
        }
    }

    val objSet get() = objSetLazy.value

    private val behaviorsLazy = lazy {
        createBehaviorContainer().also { container ->
            dataNode.withChild(container.dataNode)
        }
    }

    protected val behaviorContainer get() = behaviorsLazy.value

    /**
     * Called once when the object set is requested. The result is then cached.
     * @return A new object set, with all the desired objects.
     * */
    open fun createObjectSet() = SimulationObjectSet(
        fieldScan(this.javaClass, SimulationObject::class, SimObject::class.java, OBJECT_READERS)
            .mapNotNull { it.get(this) as? SimulationObject }
    )

    open fun createBehaviorContainer() = CellBehaviorContainer(this).also { container ->
        fieldScan(this.javaClass, CellBehavior::class, Behavior::class.java, BEHAVIOR_READERS)
            .mapNotNull { it.get(this) as? CellBehavior }.forEach(container::addInst)
    }

    fun loadTag(tag: CompoundTag) {
        tag.useSubTagIfPreset(CELL_DATA, this::loadCellData)
        tag.useSubTagIfPreset(OBJECT_DATA, this::loadObjectData)
    }

    /**
     * Called after all graphs in the level have been loaded, before the solver is built.
     * */
    open fun onWorldLoadedPreSolver() {}

    /**
     * Called after all graphs in the level have been loaded, after the solver is built.
     */
    open fun onWorldLoadedPostSolver() {}

    /**
     * Called after all graphs in the level have been loaded, before the simulations start.
     * */
    open fun onWorldLoadedPreSim() {}

    /**
     * Called after all graphs in the level have been loaded, after the simulations have started.
     * */
    open fun onWorldLoadedPostSim() {}

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
    open fun loadCellData(tag: CompoundTag) {}

    /**
     * Called when the graph is being saved. Custom data should be saved here.
     * */
    open fun saveCellData(): CompoundTag? = null

    private fun saveObjectData(tag: CompoundTag) {
        objSet.process { obj ->
            if (obj is PersistentObject) {
                tag.put(obj.type.name, obj.save())
            }
        }
    }

    private fun loadObjectData(tag: CompoundTag) {
        objSet.process { obj ->
            if (obj is PersistentObject) {
                obj.load(tag.getCompound(obj.type.name))
            }
        }
    }

    open fun onContainerUnloading() {}

    open fun onContainerUnloaded() {}

    fun bindGameObjects(objects: List<Any>) {
        val transient = this.transientPool
            ?: error("Transient pool is null in bind")

        require(replicators.isEmpty()) {
            "Lingering replicators in bind"
        }

        objects.forEach { obj ->
            fun bindReplicator(replicatorBehavior: ReplicatorBehavior) {
                behaviorContainer.addInst(replicatorBehavior)
                replicators.add(replicatorBehavior)
            }

            Replicators.replicatorScan(
                cellK = this.javaClass.kotlin,
                containerK = obj.javaClass.kotlin,
                cellInst = this,
                containerInst = obj
            ).forEach { bindReplicator(it) }
        }

        replicators.forEach { replicator ->
            replicator.subscribe(transient)
        }
    }

    fun unbindGameObjects() {
        val transient = this.transientPool
            ?: error("Transient null in unbind")

        replicators.forEach {
            behaviorContainer.destroy(it)
        }

        replicators.clear()
        transient.destroy()
    }

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

    fun notifyRemoving() {
        behaviorContainer.destroy()
        onRemoving()
        persistentPool?.destroy()
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
        if (connectionsChanged) {
            onConnectionsChanged()
        }

        if (graphChanged) {
            persistentPool?.destroy()
            transientPool?.destroy()

            persistentPool = TrackedSubscriberCollection(graph.subscribers)
            transientPool = TrackedSubscriberCollection(graph.subscribers)

            behaviorContainer.behaviors.forEach {
                it.subscribe(persistentPool!!)
            }

            onGraphChanged()
            subscribe(persistentPool!!)
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

    protected open fun subscribe(subs: SubscriberCollection) {}

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
                if (!localObj.acceptsRemoteLocation(remoteCell.pos.descriptor)) {
                    return@forEach
                }

                if (!remoteCell.hasObject(localObj.type)) {
                    return@forEach
                }

                val remoteObj = remoteCell.objSet[localObj.type]

                require(remoteCell.connections.contains(this)) { "Mismatched connection set" }

                if (!remoteObj.acceptsRemoteLocation(pos.descriptor)) {
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

                    SimulationObjectType.Diffusion -> {
                        localObj as DiffusionObject
                        remoteObj as DiffusionObject

                        require(localObj.isCompatibleWith(remoteObj) == remoteObj.isCompatibleWith(localObj)) {
                            "Compatibility conclusion conflict for DRAGONS $localObj $remoteObj"
                        }

                        if (localObj.isCompatibleWith(remoteObj)) {
                            localObj.addConnection(remoteObj)
                        }
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
    fun insertFresh(container: CellContainer, cell: Cell) {
        connectCell(cell, container)
        cell.create()
    }

    /**
     * Removes a cell from the graph. It may cause topological changes to the graph, as outlined in the top document.
     * */
    fun destroy(cellInfo: Cell, container: CellContainer) {
        disconnectCell(cellInfo, container)
        cellInfo.destroy()
    }

    fun connectCell(insertedCell: Cell, container: CellContainer) {
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

    fun disconnectCell(actualCell: Cell, actualContainer: CellContainer, notify: Boolean = true) {
        val manager = actualContainer.manager
        val actualNeighborCells = actualContainer.neighborScan(actualCell)

        val graph = actualCell.graph

        // Stop Simulation
        graph.stopSimulation()

        if (notify) {
            actualCell.notifyRemoving()
        }

        actualNeighborCells.forEach { (neighbor, neighborContainer) ->
            actualCell.removeConnection(neighbor)
            neighbor.removeConnection(actualCell)

            neighborContainer.onCellDisconnected(neighbor, actualCell)
            actualContainer.onCellDisconnected(actualCell, neighbor)
        }

        /*
        *   Cases:
        *   1. We don't have any neighbors. We can destroy the circuit.
        *   2. We have a single neighbor. We can remove ourselves from the circuit.
        *   3. We have multiple neighbors, and we are not a cut vertex. We can remove ourselves from the circuit.
        *   4. We have multiple neighbors, and we are a cut vertex. We need to remove ourselves, find the new disjoint graphs,
        *        and rebuild the circuits.
        */

        if (actualNeighborCells.isEmpty()) {
            // Case 1. Destroy this circuit.

            // Make sure we don't make any logic errors somewhere else.
            assert(graph.cells.size == 1)

            graph.destroy()
        } else if (actualNeighborCells.size == 1) {
            // Case 2.

            // Remove the cell from the circuit.
            graph.removeCell(actualCell)

            val neighbor = actualNeighborCells[0].neighbor

            neighbor.update(connectionsChanged = true, graphChanged = false)

            graph.buildSolver()
            graph.startSimulation()
            graph.setChanged()
        } else {
            // Case 3 and 4. Implement a more sophisticated algorithm, if necessary.
            graph.destroy()
            rebuildTopologies(actualNeighborCells, actualCell, manager)
        }
    }

    /**
     * Checks whether the cells share the same graph.
     * @return True, if the specified cells share the same graph. Otherwise, false.
     * */
    private fun isCommonGraph(neighbors: List<CellNeighborInfo>): Boolean {
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
        neighborInfoList: List<CellNeighborInfo>,
        removedCell: Cell,
        manager: CellGraphManager,
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
    val actualPosWorld =
        actualCell.posDescr.requireLocator<R3, BlockPosLocator> { "Planar Scan requires a block position" }.pos
    val actualFaceTarget =
        actualCell.posDescr.requireLocator<SO3, BlockFaceLocator> { "Planar Scan requires a face" }.faceWorld
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

            if (targetFaceTarget == actualFaceTarget) {
                if (actualCell.acceptsConnection(targetCell) && targetCell.acceptsConnection(actualCell)) {
                    consumer(CellNeighborInfo(targetCell, remoteContainer))
                }
            }
        }
}

fun wrappedCellScan(
    level: Level,
    actualCell: Cell,
    searchDirectionTarget: Direction,
    consumer: ((CellNeighborInfo) -> Unit),
) {
    val actualPosWorld =
        actualCell.pos.descriptor.requireLocator<R3, BlockPosLocator> { "Wrapped Scan requires a block position" }.pos
    val actualFaceActual =
        actualCell.pos.descriptor.requireLocator<SO3, BlockFaceLocator> { "Wrapped Scan requires a face" }.faceWorld
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

fun hashLocationDescriptorSet(v: Collection<LocationDescriptor>): Int {
    val cache = IntArray(v.size)
    v.forEachIndexed { index, it -> cache[index] = it.hashCode() }
    cache.sort()
    return cache.contentHashCode()
}

interface CellContainer {
    fun getCells(): ArrayList<Cell>
    fun neighborScan(actualCell: Cell): List<CellNeighborInfo>
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
    private val diffusionSims = ArrayList<VoxelizedDiffusionSimulation>()

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

    fun setChanged() {
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

    private enum class UpdateStep {
        Start,
        UpdateSubsPre,
        UpdateElectricalSims,
        UpdateThermalSims,
        UpdateDiffusionSims,
        UpdateSubsPost
    }

    /**
     * Runs one simulation step. This is called from the update thread.
     * */
    @CrossThreadAccess
    private fun update() {
        simStopLock.lock()

        var stage = UpdateStep.Start

        try {
            val fixedDt = 1.0 / 100.0

            stage = UpdateStep.UpdateSubsPre
            subscribers.update(fixedDt, SubscriberPhase.Pre)

            lastTickTime = !measureDuration {
                isElectricalSuccessful = true

                stage = UpdateStep.UpdateElectricalSims
                val electricalTime = measureDuration {
                    electricalSims.forEach {
                        val success = it.step(fixedDt)

                        isElectricalSuccessful = isElectricalSuccessful && success

                        if (!success && !it.isFloating) {
                            LOG.error("Failed to update non-floating circuit!")
                        }
                    }
                }

                stage = UpdateStep.UpdateThermalSims
                val thermalTime = measureDuration {
                    thermalSims.forEach {
                        it.step(fixedDt)
                    }
                }

                stage = UpdateStep.UpdateDiffusionSims
                val diffusionTime = measureDuration {
                    diffusionSims.forEach {
                        it.simulation.step() // controlled by Diffusion Rate

                        if (it.simulation.isActive) {
                            setChanged()
                            println("a/C: ${it.simulation.activeClusters}, a/c: ${it.simulation.activeFluidCells}, a/g: ${it.simulation.sparseGrid.activeGrids.size}, t/g: ${it.simulation.sparseGrid.subGrids.size}")
                        }
                    }
                }

                //println("diff time: ${(diffusionTime..MILLISECONDS).rounded()} ms")
            }

            stage = UpdateStep.UpdateSubsPost
            subscribers.update(fixedDt, SubscriberPhase.Post)

            updates++

        } catch (t: Throwable) {
            LOG.error("FAILED TO UPDATE SIMULATION at $stage: $t")
        } finally {
            // Maybe blow up the game instead of just allowing this to go on?
            simStopLock.unlock()
        }
    }

    fun saveSimulationData(): CompoundTag {
        return CompoundTag().also {
            it.putSubTag(NBT_DIFFUSION_DATA) { diffusionTag ->
                val simList = ListTag()

                diffusionSims.forEach { (code, sim) ->
                    val simTag = CompoundTag()

                    simTag.putInt(NBT_DIFFUSION_HULL_CODE, code)

                    // Serialize more efficiently using binary serialization
                    val sparse = sim.sparseGrid
                    val cursor = sparse.createCursor()

                    val serializedCells = ArrayList<Pair<Vector3di, Float>>()

                    sparse.subGrids.values.forEach { grid ->
                        cursor.loadg(grid)

                        for (i in sparse.windowRange) {
                            cursor.loadi(i)

                            val actualIndex = grid.ixGrid(cursor.x, cursor.y, cursor.z)

                            if (!grid.wallGrid[actualIndex] && grid.densities[actualIndex] > 0f) {
                                serializedCells.add(
                                    Pair(
                                        cursor.vector,
                                        grid.densities[actualIndex]
                                    )
                                )
                            }
                        }
                    }

                    val buffer = ByteBuffer.allocate(1 * 4 + serializedCells.size * (3 * 4 + 1 * 4))

                    buffer.putInt(serializedCells.size)
                    serializedCells.forEach { (pos, density) ->
                        buffer.putVector3di(pos)
                        buffer.putFloat(density)
                    }

                    simTag.putByteArray(NBT_DENSE_SET, buffer.array())

                    simList.add(simTag)
                }

                diffusionTag.put(NBT_DIFFUSION_SIMS_LIST, simList)
            }
        }
    }

    fun loadSimulationData(tag: CompoundTag) {
        tag.useSubTagIfPreset(NBT_DIFFUSION_DATA) { diffusionTag ->
            val simList = diffusionTag.get(NBT_DIFFUSION_SIMS_LIST) as ListTag

            simList.map { it as CompoundTag }.forEach { simTag ->
                val hullCode = simTag.getInt(NBT_DIFFUSION_HULL_CODE)

                val sim = diffusionSims.firstOrNull { it.code == hullCode }

                if (sim == null) {
                    LOG.error("Failed to resolve diffusion simulation with hull code $hullCode")
                    return@forEach
                }

                val sparse = sim.simulation.sparseGrid

                val buffer = ByteBuffer.wrap(simTag.getByteArray(NBT_DENSE_SET))
                val count = buffer.int

                repeat(count) {
                    val pos = buffer.getVector3di()
                    val density = buffer.float

                    sparse.setDensityTile(
                        pos.x, pos.y, pos.z,
                        density,
                        true
                    )
                }

                println("Loaded $count tiles")
            }
        }
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
        realizeDiffusion()

        cells.forEach { it.build() }
        electricalSims.forEach { postProcessCircuit(it) }
    }

    /**
     * This method realizes the electrical circuits for all cells that have an electrical object.
     * */
    private fun realizeElectrical() {
        electricalSims.clear()

        realizeComponents(SimulationObjectType.Electrical, factory = { set ->
            val circuit = Circuit()
            set.forEach { it.objSet.electricalObject.setNewCircuit(circuit) }
            electricalSims.add(circuit)
        })
    }

    private fun realizeThermal() {
        thermalSims.clear()

        realizeComponents(SimulationObjectType.Thermal, factory = { set ->
            val simulation = Simulator()
            set.forEach { it.objSet.thermalObject.setNewSimulation(simulation) }
            thermalSims.add(simulation)
        })
    }

    private fun realizeDiffusion() {
        diffusionSims.clear()

        realizeComponents(SimulationObjectType.Diffusion, factory = { set ->
            if (set.isEmpty()) {
                return@realizeComponents
            }

            val diffusionObjects = set.map { it.objSet.diffusionObject }

            // Build volumetric graph:
            val mapNodes = diffusionObjects.associateWithBi {
                VoxelPatchNode(it.createPatchModule())
            }

            mapNodes.forward.forEach { (obj, node) ->
                obj.actualConnections.forEach { (connObj, connDir) ->
                    node.connections.add(
                        VoxelPatchEdge(
                            patchNode = mapNodes.forward[connObj]!!,
                            direction = connDir
                        )
                    )
                }
            }

            val scan = VoxelVolumetricScan(
                // Ensure it gets replicated on loading (so the subspace matches the stored data):
                mapNodes.forward[
                    mapNodes.backward.values.minByOrNull {
                        it.cell.posDescr.hashCode()
                    }!!
                ]!!,
                mapNodes.forward.keys.first().size
            )

            val grid = SparseGrid3dFluid(FLUID_GRID_SIZE_LOG)
            val realized = HashMap<VoxelPatchNode, RealizedVoxelPatchNode>()

            val scanResult = scan.voxelizeHull(grid, realized)

            if (!scanResult.hullClosed) {
                grid.exportForInspection()

                error("Open hull, check file\n${grid.subGrids}")
            }

            grid.exportForInspection() // todo remove in "production"

            val simulation = DiffusionSimulation(
                grid,
                mapNodes.forward.keys.first().def.simulationOptions
            )

            val code = hashLocationDescriptorSet(
                diffusionObjects.map { it.cell.posDescr }
            )

            diffusionSims.add(
                VoxelizedDiffusionSimulation(
                    code,
                    simulation
                )
            )

            realized.values.forEach { realizedNode ->
                mapNodes.backward[realizedNode.node]!!.bindAccessor(
                    DiffusionAccessorImpl(
                        simulation,
                        realizedNode
                    )
                )
            }
        }, extraCondition = { a, b ->
            a as DiffusionObject
            b as DiffusionObject

            a.isCompatibleWith(b)
        })
    }

    private data class VoxelizedDiffusionSimulation(
        val code: Int,
        val simulation: DiffusionSimulation,
    )

    private class DiffusionAccessorImpl(
        val simulation: DiffusionSimulation,
        override val realizedNode: RealizedVoxelPatchNode,
    ) : DiffusionAccessor {
        private val grid get() = simulation.sparseGrid

        private fun getTileGrid(tilePatch: Vector3di): Vector3di {
            val tileGrid = realizedNode.mapTileGrid(tilePatch)

            if (!realizedNode.isTileGridWithinPatchBounds(tileGrid)) {
                error("Tried to access tile outside of patch $realizedNode $tileGrid $tilePatch")
            }

            return tileGrid
        }

        override fun readDensity(tilePatch: Vector3di): Float {
            val tileGrid = getTileGrid(tilePatch)
            val target = grid.getOrCreateTile(tileGrid)

            return target.densities[tileGrid]
        }

        override fun addDensityIncr(tilePatch: Vector3di, incr: Float, activate: Boolean) {
            val tileGrid = getTileGrid(tilePatch)
            grid.addDensityIncrTile(tileGrid.x, tileGrid.y, tileGrid.z, incr, activate)
        }

        override fun setDensity(tilePatch: Vector3di, amount: Float, activate: Boolean) {
            val tileGrid = getTileGrid(tilePatch)
            grid.setDensityTile(tileGrid.x, tileGrid.y, tileGrid.z, amount, activate)
        }

        override fun activate(tilePatch: Vector3di) {
            val tileGrid = getTileGrid(tilePatch)
            grid.activate(tileGrid.x, tileGrid.y, tileGrid.z)
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
    private fun <TComponent> realizeComponents(
        type: SimulationObjectType,
        factory: ((HashSet<Cell>) -> TComponent),
        extraCondition: ((SimulationObject, SimulationObject) -> Boolean)? = null,
    ) {
        val pending = HashSet(cells.filter { it.hasObject(type) })
        val queue = ArrayDeque<Cell>()

        // todo: can we use pending instead?
        val visited = HashSet<Cell>()

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

                cell.connections.forEach { connectedCell ->
                    if (connectedCell.hasObject(type)) {
                        if (extraCondition != null && !extraCondition(cell.objSet[type], connectedCell.objSet[type])) {
                            return@forEach
                        }

                        queue.add(connectedCell)
                    }
                }
            }

            results.add(factory(visited))
        }
    }

    private fun postProcessCircuit(circuit: Circuit) {
        if (circuit.isFloating) {
            fixFloating(circuit)
        }
    }

    private fun fixFloating(circuit: Circuit) {
        var found = false
        for (comp in circuit.components) {
            if (comp is VoltageSource) {
                comp.ground(1)
                found = true
                break
            }
        }
        if (!found) {
            LOG.warn("Floating circuit and no VSource; the matrix is likely under-constrained.")
        }
    }

    /**
     * Gets the cell at the specified CellPos.
     * @return The cell, if found, or throws an exception, if the cell does not exist.
     * */
    fun getCell(pos: CellPos): Cell {
        val result = posCells[pos]

        if (result == null) {
            LOG.error("Could not get cell at $pos") // exception may be swallowed
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
        if (isSimRunning) {
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

        LOG.info("Stopped simulation for $this")
    }

    /**
     * Starts the simulation. Will result in an error if it is already running.,
     * */
    fun startSimulation() {
        if (simTask != null) {
            error("Tried to start simulation, but it was already running")
        }

        simTask = pool.scheduleAtFixedRate(this::update, 0, 10, TimeUnit.MILLISECONDS)

        LOG.info("Started simulation for $this")
    }

    /**
     * Runs the specified [action], ensuring that the simulation is paused.
     * The previous running state is preserved; if the simulation was paused, it will not be started after the [action] is completed.
     * If it was running, then the simulation will resume.
     * */
    fun runSuspended(action: (() -> Unit)) {
        val running = isSimRunning

        if (running) {
            stopSimulation()
        }

        action()

        if (running) {
            startSimulation()
        }
    }

    fun toNbt(): CompoundTag {
        val circuitCompound = CompoundTag()

        require(!isSimRunning)

        circuitCompound.putUUID(NBT_ID, id)

        val cellListTag = ListTag()

        cells.forEach { cell ->
            val cellTag = CompoundTag()
            val connectionsTag = ListTag()

            cell.connections.forEach { conn ->
                val connectionCompound = CompoundTag()
                connectionCompound.putCellPos(NBT_POSITION, conn.pos)
                connectionsTag.add(connectionCompound)
            }

            cellTag.putCellPos(NBT_POSITION, cell.pos)
            cellTag.putString(NBT_ID, cell.id.toString())
            cellTag.put(NBT_CONNECTIONS, connectionsTag)

            try {
                cellTag.put(NBT_CELL_DATA, cell.createTag())
            } catch (t: Throwable) {
                LOG.error("Cell save error: $t")
            }

            cellListTag.add(cellTag)
        }

        circuitCompound.put(NBT_CELLS, cellListTag)

        return circuitCompound
    }

    fun serverStop() {
        if (simTask != null) {
            stopSimulation()
        }
    }

    companion object {
        private const val NBT_CELL_DATA = "data"
        private const val NBT_ID = "id"
        private const val NBT_CELLS = "cells"
        private const val NBT_POSITION = "pos"
        private const val NBT_CONNECTIONS = "connections"
        private const val NBT_DIFFUSION_DATA = "diffusionData"
        private const val NBT_DIFFUSION_SIMS_LIST = "diffusionSimList"
        private const val NBT_DIFFUSION_HULL_CODE = "hullCode"
        private const val NBT_DENSE_SET = "denseSet"

        const val FLUID_GRID_SIZE_LOG = 3

        init {
            // We do get an exception from thread pool creation, but explicit handling is better here.
            if (Configuration.instance.simulationThreads == 0) {
                error("Simulation threads is 0")
            }

            LOG.info("Using ${Configuration.instance.simulationThreads} simulation threads")
        }

        private val threadNumber = AtomicInteger()

        private fun createThread(r: Runnable): Thread {
            val t = Thread(r, "cell-graph-${threadNumber.getAndIncrement()}")

            if (t.isDaemon) {
                t.isDaemon = false
            }

            if (t.priority != Thread.NORM_PRIORITY) {
                t.priority = Thread.NORM_PRIORITY
            }

            return t
        }

        private val pool = Executors.newScheduledThreadPool(
            Configuration.instance.simulationThreads, ::createThread
        )

        fun fromNbt(graphCompound: CompoundTag, manager: CellGraphManager, level: ServerLevel): CellGraph {
            val id = graphCompound.getUUID(NBT_ID)
            val result = CellGraph(id, manager, level)

            val cellListTag = graphCompound.get(NBT_CELLS) as ListTag?
                ?: // No cells are available
                return result

            // Used to assign the connections after all cells have been loaded:
            val cellConnections = HashMap<Cell, ArrayList<CellPos>>()

            // Used to load cell custom data:
            val cellData = HashMap<Cell, CompoundTag>()

            cellListTag.forEach { cellNbt ->
                val cellCompound = cellNbt as CompoundTag
                val pos = cellCompound.getCellPos(NBT_POSITION)
                val cellId = ResourceLocation.tryParse(cellCompound.getString(NBT_ID))!!

                val connectionPositions = ArrayList<CellPos>()
                val connectionsTag = cellCompound.get(NBT_CONNECTIONS) as ListTag

                connectionsTag.forEach {
                    val connectionCompound = it as CompoundTag
                    val connectionPos = connectionCompound.getCellPos(NBT_POSITION)
                    connectionPositions.add(connectionPos)
                }

                val cell = CellRegistry.getProvider(cellId).create(
                    pos,
                    BiomeEnvironments.cellEnv(level, pos).fieldMap()
                )

                cellConnections[cell] = connectionPositions

                result.addCell(cell)

                cellData[cell] = cellCompound.getCompound(NBT_CELL_DATA)
            }

            // Now assign all connections and the graph to the cells:
            cellConnections.forEach { (cell, connectionPositions) ->
                val connections = ArrayList<Cell>(connectionPositions.size)

                connectionPositions.forEach { connections.add(result.getCell(it)) }

                // now set graph and connection
                cell.graph = result
                cell.connections = connections
                cell.update(connectionsChanged = true, graphChanged = true)

                try {
                    cell.loadTag(cellData[cell]!!)
                } catch (t: Throwable) {
                    LOG.error("Cell loading exception: $t")
                }

                cell.onLoadedFromDisk()
            }

            result.cells.forEach { it.create() }

            return result
        }
    }
}

fun runSuspended(graphs: List<CellGraph>, action: () -> Unit) {
    if (graphs.isEmpty()) {
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
        val elapsedSeconds = !statisticsWatch.sample()

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
        LOG.info("Removed graph ${graph.id}!")
        setDirty()
    }

    override fun save(tag: CompoundTag): CompoundTag {
        val graphListTag = ListTag()

        graphs.values.forEach { graph ->
            graph.runSuspended {
                graphListTag.add(graph.toNbt().also {
                    it.put(SIM_DATA, graph.saveSimulationData())
                })
            }
        }

        tag.put("Graphs", graphListTag)
        LOG.info("Saved ${graphs.size} graphs to disk.")
        return tag
    }

    /**
     * Gets the graph with the specified ID, or throws an exception.
     * */
    fun getGraph(id: UUID): CellGraph {
        return graphs[id] ?: error("Graph with id $id not found")
    }

    fun serverStop() {
        graphs.values.forEach { it.serverStop() }
    }

    companion object {
        private const val SIM_DATA = "SimData"

        private fun load(tag: CompoundTag, level: ServerLevel): CellGraphManager {
            val manager = CellGraphManager(level)

            val graphListTag = tag.get("Graphs") as ListTag?

            if (graphListTag == null) {
                LOG.info("No nodes to be loaded!")
                return manager
            }

            val simData = HashMap<CellGraph, CompoundTag>()

            graphListTag.forEach { circuitNbt ->
                val graphCompound = circuitNbt as CompoundTag
                val graph = CellGraph.fromNbt(graphCompound, manager, level)

                graphCompound.useSubTagIfPreset(SIM_DATA) {
                    simData[graph] = it
                }

                if (graph.cells.isEmpty()) {
                    LOG.error("Loaded circuit with no cells!")
                    return@forEach
                }

                manager.addGraph(graph)

                LOG.info("Loaded ${graph.cells.size} cells for ${graph.id}!")
            }

            manager.graphs.values.forEach {
                it.cells.forEach { cell -> cell.onWorldLoadedPreSolver() }
            }

            manager.graphs.values.forEach { it.buildSolver() }

            simData.keys.forEach { k ->
                k.loadSimulationData(simData[k]!!)
            }

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
        fun getFor(level: ServerLevel): CellGraphManager = level.dataStorage.computeIfAbsent(
            { load(it, level) },
            { CellGraphManager(level) },
            "CellManager"
        )
    }
}

/**
 * The Cell Provider is a factory of cells, and also has connection rules for cells.
 * */
abstract class CellProvider {
    val id get() = CellRegistry.getId(this)

    protected abstract fun createInstance(ci: CellCreateInfo): Cell

    /**
     * Creates a new Cell, at the specified position.
     * */
    fun create(pos: CellPos, envNode: DataFieldMap): Cell {
        return createInstance(
            CellCreateInfo(
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
    fun create(ci: CellCreateInfo): Cell
}

class BasicCellProvider(private val factory: CellFactory) : CellProvider() {
    override fun createInstance(ci: CellCreateInfo) = factory.create(ci)
}

class InjectCellProvider<T : Cell>(val c: Class<T>, val extraParams: List<Any>) : CellProvider() {
    constructor(c: Class<T>) : this(c, listOf())

    @Suppress("UNCHECKED_CAST")
    override fun createInstance(ci: CellCreateInfo) =
        ServiceCollection()
            .withSingleton { ci }
            .withSingleton { this }
            .activate(c, extraParams) as T
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

