package org.eln2.mc.common.cells.foundation

import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.saveddata.SavedData
import net.minecraftforge.server.ServerLifecycleHooks
import org.ageseries.libage.data.MutableMapPairBiMap
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.PowerVoltageSource
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.ageseries.libage.sim.electrical.mna.component.VoltageSource
import org.ageseries.libage.sim.thermal.Simulator
import org.eln2.mc.*
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.data.*
import org.eln2.mc.mathematics.approxEq
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.ArrayList
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * [SubscriberCollection] that tracks the added subscribers, making it possible to remove all of them at a later time.
 * @param underlyingCollection The parent subscriber collection that will actually run the subscribers.
 * */
class TrackedSubscriberCollection(private val underlyingCollection: SubscriberCollection) : SubscriberCollection {
    private val subscribers = HashMap<Subscriber, SubscriberOptions>()

    override fun addSubscriber(parameters: SubscriberOptions, subscriber: Subscriber) {
        require(subscribers.put(subscriber, parameters) == null) { "Duplicate subscriber $subscriber" }
        underlyingCollection.addSubscriber(parameters, subscriber)
    }

    override fun remove(subscriber: Subscriber) {
        require(subscribers.remove(subscriber) != null) { "Subscriber $subscriber was never added" }
        underlyingCollection.remove(subscriber)
    }

    fun clear() {
        subscribers.keys.forEach { underlyingCollection.remove(it) }
        subscribers.clear()
    }
}

data class CellCreateInfo(val locator: Locator, val id: ResourceLocation, val environment: HashDataTable)

/**
 * Marks a field in a [Cell] as [SimulationObject]. The object will be registered automatically.
 * */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class SimObject

/**
 * Marks a field in a [Cell] as [CellBehavior]. The behavior will be registered automatically.
 * */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class Behavior

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class Inspect

/**
 * The cell is a physical unit, that may participate in multiple simulations. Each simulation will
 * have a Simulation Object associated with it.
 * Cells create connections with other cells, and objects create connections with other objects of the same simulation type.
 * */
@ServerOnly
abstract class Cell(val locator: Locator, val id: ResourceLocation, val environmentData: HashDataTable) {
    companion object {
        private val OBJECT_READERS = ConcurrentHashMap<Class<*>, List<FieldInfo<Cell>>>()
        private val BEHAVIOR_READERS = ConcurrentHashMap<Class<*>, List<FieldInfo<Cell>>>()

        private const val CELL_DATA = "cellData"
        private const val OBJECT_DATA = "objectData"

        private val ID_ATOMIC = AtomicInteger()
    }

    constructor(ci: CellCreateInfo) : this(ci.locator, ci.id, ci.environment)

    val uniqueCellId = ID_ATOMIC.getAndIncrement()

    // Persistent behaviors are used by cell logic, and live throughout the lifetime of the cell:
    private var persistentPool: TrackedSubscriberCollection? = null

    // Transient behaviors are used when the cell is in range of a player (and the game object exists):
    private var transientPool: TrackedSubscriberCollection? = null

    lateinit var graph: CellGraph
    var connections: ArrayList<Cell> = ArrayList(0)

    val ruleSet by lazy {
        LocatorRelationRuleSet()
    }

    protected val services by lazy {
        ServiceCollection()
            .withSingleton { this }
            .withSingleton(this.javaClass) { this }
            .withSingleton { locator }
            .also { registerServices(it) }
    }

    /**
     * Called when the [services] is being initialized, in order to register user services.
     * */
    protected open fun registerServices(services: ServiceCollection) {}

    /**
     * Instantiates the specified class using dependency injection. Calling this method will initialize the [servicesLazy].
     * By default, the following services are included:
     * - this
     * - [javaClass]
     * - [locator]
     * - [registerServices] (user-specified services)
     * */
    protected inline fun <reified T> activate(vararg extraParams: Any): T =
        services.activate(extraParams.asList())

    /**
     * Instantiates the specified class using dependency injection, as per [activate].
     * The result will also be added to the service collection.
     * */
    protected inline fun <reified T> activateService(vararg extraParams: Any): T = services.activate<T>(extraParams.asList()).also {
        if(it is ReplicatorBehavior) {
            error("Cannot activate service a replicator behavior!")
        }

        services.withService(T::class.java) { it }
    }

    fun allowsConnection(remote: Cell): Boolean {
        val result = connectionPredicate(remote)

        // Discuss with me if you want more info
        if(!result && connections.contains(remote)) {
            LOG.warn("Forcing connection rule")
            return true
        }

        return result
    }

    /**
     * Checks if this cell accepts a connection from the remote cell.
     * **SPECIAL CARE MUST BE TAKEN to ensure that the results are consistent with the actual [connections]**
     * @return True if the connection is accepted. Otherwise, false.
     * */
    protected open fun connectionPredicate(remoteCell: Cell) : Boolean {
        return ruleSet.accepts(locator, remoteCell.locator)
    }

    private val replicators = ArrayList<ReplicatorBehavior>()

    /**
     * Marks this cell as dirty.
     * If [hasGraph], [CellGraph.setChanged] is called to ensure the cell data will be saved.
     * */
    fun setChanged() {
        if (hasGraph) {
            graph.setChanged()
        }
    }

    /**
     * Marks this cell as dirty, if [value] is true.
     * */
    fun setChangedIf(value: Boolean) {
        if(value) {
            setChanged()
        }
    }

    fun setChangedIf(value: Boolean, action: () -> Unit) {
        if(value) {
            setChanged()
            action()
        }
    }

    val hasGraph get() = this::graph.isInitialized

    fun removeConnection(cell: Cell) {
        if (!connections.remove(cell)) {
            error("Tried to remove non-existent connection")
        }
    }

    var container: CellContainer? = null

    private val objectsLazy = lazy {
        val objectFields = fieldScan(this.javaClass, SimulationObject::class, SimObject::class.java, OBJECT_READERS)

        val fields = HashMap<SimulationObject<*>, FieldInfo<Cell>>()

        SimulationObjectSet(objectFields.mapNotNull {
            val o = it.reader.get(this) as? SimulationObject<*>

            if(o != null) {
                require(fields.put(o, it) == null) {
                    "Duplicate obj $o"
                }
            }

            o
        })
    }

    val objects get() = objectsLazy.value

    var isBeingRemoved = false
        private set

    protected val behaviors by lazy {
        createBehaviorContainer()
    }

    open fun createBehaviorContainer() = CellBehaviorContainer(this).also { container ->
        fieldScan(this.javaClass, CellBehavior::class, Behavior::class.java, BEHAVIOR_READERS)
            .mapNotNull { it.reader.get(this) as? CellBehavior }.forEach(container::addToCollection)
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
        objects.forEachObject { obj ->
            if (obj is PersistentObject) {
                tag.put(obj.type.domain, obj.saveObjectNbt())
            }
        }
    }

    private fun loadObjectData(tag: CompoundTag) {
        objects.forEachObject { obj ->
            if (obj is PersistentObject) {
                obj.loadObjectNbt(tag.getCompound(obj.type.domain))
            }
        }
    }

    open fun onContainerUnloading() {}

    open fun onContainerUnloaded() {}

    fun bindGameObjects(objects: List<Any>) {
        // Not null, it is initialized when added to graph (so the SubscriberCollection is available)
        val transient = this.transientPool
            ?: error("Transient pool is null in bind")

        require(replicators.isEmpty()) { "Lingering replicators in bind" }

        objects.forEach { obj ->
            fun bindReplicator(behavior: ReplicatorBehavior) {
                behaviors.addToCollection(behavior)
                replicators.add(behavior)
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
            behaviors.destroy(it)
        }

        replicators.clear()
        transient.clear()
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
        isBeingRemoved = true
        behaviors.destroy()
        onRemoving()
        persistentPool?.clear()
    }

    /**
     * Called while the cell is being destroyed, just after the simulation was stopped.
     * Subscribers may be cleaned up here.
     * *Almost* guaranteed to be on the game thread.
     * */
    protected open fun onRemoving() {}

    fun destroy() {
        onDestroyed()
    }

    /**
     * Called after the cell was destroyed.
     */
    protected open fun onDestroyed() {
        objects.forEachObject { it.destroy() }
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
            persistentPool?.clear()
            transientPool?.clear()

            persistentPool = TrackedSubscriberCollection(graph.simulationSubscribers)
            transientPool = TrackedSubscriberCollection(graph.simulationSubscribers)

            behaviors.behaviors.forEach {
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
        objects.forEachObject { it.clear() }
    }

    /**
     * Called when the solver is being built, in order to record all object-object connections.
     * */
    fun recordObjectConnections() {
        objects.forEachObject { localObj ->
            for (remoteCell in connections) {
                require(remoteCell.connections.contains(this)) {
                    "Mismatched connection set"
                }

                if (!localObj.acceptsRemoteLocation(remoteCell.locator)) {
                    continue
                }

                if (!remoteCell.hasObject(localObj.type)) {
                    continue
                }

                val remoteObj = remoteCell.objects[localObj.type]

                if (!remoteObj.acceptsRemoteLocation(locator)) {
                    continue
                }

                // We can form a connection here.

                when (localObj.type) {
                    SimulationObjectType.Electrical -> {
                        (localObj as ElectricalObject).addConnection(
                            remoteCell.objects.electricalObject
                        )
                    }

                    SimulationObjectType.Thermal -> {
                        (localObj as ThermalObject).addConnection(
                            remoteCell.objects.thermalObject
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
    fun build() = objects.forEachObject { it.build() }

    /**
     * Checks if this cell has the specified simulation object type.
     * @return True if this cell has the required object. Otherwise, false.
     * */
    fun hasObject(type: SimulationObjectType) = objects.hasObject(type)
}

fun Cell.self() = this

fun isConnectionAccepted(a: Cell, b: Cell) = a.allowsConnection(b) && b.allowsConnection(a)

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
                existingGraph.forEach { cell ->
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

        if(!graph.isSimulating) {
            noop()
        }

        // Stop Simulation
        graph.stopSimulation()

        if (notify) {
            actualCell.notifyRemoving()
        }

        val markedNeighbors = actualCell.connections.toHashSet()

        actualNeighborCells.forEach { (neighbor, neighborContainer) ->
            val containsA = actualCell.connections.contains(neighbor)
            val containsB = neighbor.connections.contains(actualCell)

            if(containsA && containsB) {
                actualCell.removeConnection(neighbor)
                neighbor.removeConnection(actualCell)

                neighborContainer.onCellDisconnected(neighbor, actualCell)
                actualContainer.onCellDisconnected(actualCell, neighbor)

                markedNeighbors.remove(neighbor)
            }
            else if(containsA != containsB) {
                error("Mismatched connection vs query result")
            }
        }

        if(markedNeighbors.isNotEmpty()) {
            error("Lingering connections $actualCell $markedNeighbors")
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
            assert(graph.size == 1)

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

    inline fun retopologize(cell: Cell, container: CellContainer, action: () -> Unit) {
        disconnectCell(cell, container, false)
        action()
        connectCell(cell, container)
    }

    fun retopologize(cell: Cell, container: CellContainer) {
        disconnectCell(cell, container, false)
        connectCell(cell, container)
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
            graph.forEach { cell ->
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

inline fun planarCellScan(level: Level, actualCell: Cell, searchDirection: Direction, consumer: ((CellNeighborInfo) -> Unit)) {
    val actualPosWorld = actualCell.locator.requireLocator<BlockLocator> { "Planar Scan requires a block position" }
    val actualFaceTarget = actualCell.locator.requireLocator<FaceLocator> { "Planar Scan requires a face" }
    val remoteContainer = level.getBlockEntity(actualPosWorld + searchDirection) as? CellContainer ?: return

    remoteContainer
        .getCells()
        .filter { it.locator.has<BlockLocator>() && it.locator.has<FaceLocator>() }
        .forEach { targetCell ->
            val targetFaceTarget = targetCell.locator.requireLocator<FaceLocator>()

            if (targetFaceTarget == actualFaceTarget) {
                if (isConnectionAccepted(actualCell, targetCell)) {
                    consumer(CellNeighborInfo(targetCell, remoteContainer))
                }
            }
        }
}

inline fun wrappedCellScan(
    level: Level,
    actualCell: Cell,
    searchDirectionTarget: Direction,
    consumer: ((CellNeighborInfo) -> Unit),
) {
    val actualPosWorld = actualCell.locator.requireLocator<BlockLocator> { "Wrapped Scan requires a block position" }
    val actualFaceActual = actualCell.locator.requireLocator<FaceLocator> { "Wrapped Scan requires a face" }
    val wrapDirection = actualFaceActual.opposite
    val remoteContainer = level.getBlockEntity(actualPosWorld + searchDirectionTarget + wrapDirection) as? CellContainer
        ?: return

    remoteContainer
        .getCells()
        .filter { it.locator.has<BlockLocator>() && it.locator.has<FaceLocator>() }
        .forEach { targetCell ->
            val targetFaceTarget = targetCell.locator.requireLocator<FaceLocator>()

            if (targetFaceTarget == searchDirectionTarget) {
                if (isConnectionAccepted(actualCell, targetCell)) {
                    consumer(CellNeighborInfo(targetCell, remoteContainer))
                }
            }
        }
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
data class CellNeighborInfo(val neighbor: Cell, val neighborContainer: CellContainer) {
    companion object {
        fun of(cell: Cell) = CellNeighborInfo(cell, cell.container ?: error("Cannot create CellNeighborInfo of $cell"))
    }
}

class CellList : Iterable<Cell> {
    private val cells = MutableMapPairBiMap<Cell, Locator>()

    val size get() = cells.size

    fun add(cell: Cell) = cells.add(cell, cell.locator)

    fun remove(cell: Cell) = check(cells.removeForward(cell)) { "Cell $cell ${cell.locator} was not present"}

    fun contains(cell: Cell) = cells.forward.contains(cell)

    fun contains(locator: Locator) = cells.backward.contains(locator)

    fun getByLocator(locator: Locator) = cells.backward[locator]

    fun addAll(source: CellList) = source.forEach { this.add(it) }

    override fun iterator(): Iterator<Cell> = cells.forward.keys.iterator()
}

/**
 * The cell graph represents a physical network of cells.
 * It may have multiple simulation subsets, formed between objects in the cells of this graph.
 * The cell graph manages the solver and simulation.
 * It also has serialization/deserialization logic for saving to the disk using NBT.
 * */
class CellGraph(val id: UUID, val manager: CellGraphManager, val level: ServerLevel) : Iterable<Cell> {
    private val cells = CellList()
    private val electricalSims = ArrayList<Circuit>()
    private val thermalSims = ArrayList<Simulator>()

    private val simulationStopLock = ReentrantLock()

    // This is the simulation task. It will be null if the simulation is stopped
    private var simulationTask: ScheduledFuture<*>? = null

    val isSimulating get() = simulationTask != null

    @CrossThreadAccess
    private var updates = 0L

    private var updatesCheckpoint = 0L

    val simulationSubscribers = SubscriberPool()

    @CrossThreadAccess
    var lastTickTime = 0.0
        private set

    var isLoading = false
        private set

    /**
     * Gets an iterator over the cells in this graph.
     * */
    override fun iterator(): Iterator<Cell> = cells.iterator()

    /**
     * Gets the number of cells in the graph.
     * */
    val size get() = cells.size

    fun isEmpty() = size == 0
    fun isNotEmpty() = !isEmpty()

    /**
     * Adds a cell to the internal sets, assigns its graph, and invalidates the saved data.
     * **This does not update the solver!
     * It is assumed that multiple operations of this type will be performed, then, the solver update will occur explicitly.**
     * The simulation must be stopped before calling this.
     * */
    fun addCell(cell: Cell) {
        validateMutationAccess()
        cells.add(cell)
        cell.graph = this
        manager.setDirty()
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
        manager.setDirty()
    }

    /**
     * Copies the cells of this graph to the other graph, and invalidates the saved data.
     * The simulation must be stopped before calling this.
     * */
    fun copyTo(graph: CellGraph) {
        this.validateMutationAccess()
        graph.validateMutationAccess()
        graph.cells.addAll(this.cells)
        graph.manager.setDirty()
    }

    /**
     * Gets the cell with the specified [locator].
     * @return The cell, if found, or throws an exception, if the cell does not exist.
     * */
    fun getCellByLocator(locator: Locator): Cell {
        val result = cells.getByLocator(locator)

        if (result == null) {
            LOG.error("Could not get cell at $locator") // exception may be swallowed
            error("Could not get cell at $locator")
        }

        return result
    }

    /**
     * Checks if the graph contains a cell with the specified [locator].
     * @return True if a cell with the [locator] exists in this graph. Otherwise, false.
     * */
    fun containsCellByLocator(locator: Locator) = cells.contains(locator)

    fun setChanged() {
        if(!isLoading) {
            manager.setDirty()
        }
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
     * Checks if the simulation is running. Presumably, this is used by logic that wants to mutate the graph.
     * It also checks if the caller is the server thread.
     * */
    private fun validateMutationAccess() {
        if (simulationTask != null) {
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
        UpdateSubsPost
    }

    /**
     * Runs one simulation step. This is called from the update thread.
     * */
    @CrossThreadAccess
    private fun update() {
        simulationStopLock.lock()

        var stage = UpdateStep.Start

        try {
            val fixedDt = 1.0 / 100.0

            stage = UpdateStep.UpdateSubsPre
            simulationSubscribers.update(fixedDt, SubscriberPhase.Pre)

            lastTickTime = !measureDuration {

                stage = UpdateStep.UpdateElectricalSims
                val electricalTime = measureDuration {
                    electricalSims.forEach {
                        val success = it.step(fixedDt)

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
            }

            stage = UpdateStep.UpdateSubsPost
            simulationSubscribers.update(fixedDt, SubscriberPhase.Post)

            updates++

        } catch (t: Throwable) {
            LOG.error("FAILED TO UPDATE SIMULATION at $stage: $t")
        } finally {
            // Maybe blow up the game instead of just allowing this to go on?
            simulationStopLock.unlock()
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
            set.forEach { it.objects.electricalObject.setNewCircuit(circuit) }
            electricalSims.add(circuit)
        })
    }

    private fun realizeThermal() {
        thermalSims.clear()

        realizeComponents(SimulationObjectType.Thermal, factory = { set ->
            val simulation = Simulator()
            set.forEach { it.objects.thermalObject.setNewSimulation(simulation) }
            thermalSims.add(simulation)
        })
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
        extraCondition: ((SimulationObject<*>, SimulationObject<*>) -> Boolean)? = null,
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
                        if (extraCondition != null && !extraCondition(
                                cell.objects[type],
                                connectedCell.objects[type]
                            )
                        ) {
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
     * Removes the graph from tracking and invalidates the saved data.
     * The simulation must be stopped before calling this.
     * */
    fun destroy() {
        validateMutationAccess()

        manager.removeGraph(this)
        manager.setDirty()
    }

    fun ensureStopped() {
        if (isSimulating) {
            stopSimulation()
        }
    }

    /**
     * Stops the simulation. This is a sync point, so usage of this should be sparse.
     * Will result in an error if it was not running.
     * */
    fun stopSimulation() {
        if (simulationTask == null) {
            error("Tried to stop simulation, but it was not running")
        }

        simulationStopLock.lock()
        simulationTask!!.cancel(true)
        simulationTask = null
        simulationStopLock.unlock()

        LOG.info("Stopped simulation for $this")
    }

    /**
     * Starts the simulation. Will result in an error if it is already running.,
     * */
    fun startSimulation() {
        if (simulationTask != null) {
            error("Tried to start simulation, but it was already running")
        }

        simulationTask = pool.scheduleAtFixedRate(this::update, 0, 10, TimeUnit.MILLISECONDS)

        LOG.info("Started simulation for $this")
    }

    /**
     * Runs the specified [action], ensuring that the simulation is paused.
     * The previous running state is preserved; if the simulation was paused, it will not be started after the [action] is completed.
     * If it was running, then the simulation will resume.
     * */
    @OptIn(ExperimentalContracts::class)
    fun runSuspended(action: (() -> Unit)) {
        contract {
            callsInPlace(action)
        }

        val running = isSimulating

        if (running) {
            stopSimulation()
        }

        action()

        if (running) {
            startSimulation()
        }
    }

    // TODO revamp the schema

    fun toNbt(): CompoundTag {
        val circuitCompound = CompoundTag()

        require(!isSimulating)

        circuitCompound.putUUID(NBT_ID, id)

        val cellListTag = ListTag()

        cells.forEach { cell ->
            val cellTag = CompoundTag()
            val connectionsTag = ListTag()

            cell.connections.forEach { conn ->
                val connectionCompound = CompoundTag()
                connectionCompound.putLocatorSet(NBT_POSITION, conn.locator)
                connectionsTag.add(connectionCompound)
            }

            cellTag.putLocatorSet(NBT_POSITION, cell.locator)
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
        if (simulationTask != null) {
            stopSimulation()
        }
    }

    companion object {
        private const val NBT_CELL_DATA = "data"
        private const val NBT_ID = "id"
        private const val NBT_CELLS = "cells"
        private const val NBT_POSITION = "pos"
        private const val NBT_CONNECTIONS = "connections"

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
            val graphId = graphCompound.getUUID(NBT_ID)
            val result = CellGraph(graphId, manager, level)

            result.isLoading = true

            val cellListTag = graphCompound.get(NBT_CELLS) as ListTag?
                ?: // No cells are available
                return result

            // Used to assign the connections after all cells have been loaded:
            val cellConnections = HashMap<Cell, ArrayList<Locator>>()

            // Used to load cell custom data:
            val cellData = HashMap<Cell, CompoundTag>()

            cellListTag.forEach { cellNbt ->
                val cellCompound = cellNbt as CompoundTag
                val pos = cellCompound.getLocatorSet(NBT_POSITION)
                val cellId = ResourceLocation.tryParse(cellCompound.getString(NBT_ID))!!

                val connectionPositions = ArrayList<Locator>()
                val connectionsTag = cellCompound.get(NBT_CONNECTIONS) as ListTag

                connectionsTag.forEach {
                    val connectionCompound = it as CompoundTag
                    val connectionPos = connectionCompound.getLocatorSet(NBT_POSITION)
                    connectionPositions.add(connectionPos)
                }

                val cell = CellRegistry.getCellProvider(cellId).create(
                    pos,
                    BiomeEnvironments.getInformationForBlock(level, pos).fieldMap()
                )

                cellConnections[cell] = connectionPositions

                result.addCell(cell)

                cellData[cell] = cellCompound.getCompound(NBT_CELL_DATA)
            }

            // Now assign all connections and the graph to the cells:
            cellConnections.forEach { (cell, connectionPositions) ->
                val connections = ArrayList<Cell>(connectionPositions.size)

                connectionPositions.forEach { connections.add(result.getCellByLocator(it)) }

                // Now set graph and connection
                cell.graph = result
                cell.connections = connections
                cell.update(connectionsChanged = true, graphChanged = true)

                try {
                    cell.loadTag(cellData[cell]!!)
                } catch (t: Throwable) {
                    LOG.error("Cell loading exception: $t")
                }
            }

            result.cells.forEach { it.onLoadedFromDisk() }
            result.cells.forEach { it.create() }

            result.isLoading = false

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
                graphListTag.add(graph.toNbt())
            }
        }

        tag.put("Graphs", graphListTag)
        LOG.info("Saved ${graphs.size} graphs to disk.")
        return tag
    }

    /**
     * Gets the graph with the specified ID, or throws an exception.
     * */
    fun getGraph(id: UUID) = graphs[id]
        ?: error("Graph with id $id not found")

    fun serverStop() {
        graphs.values.forEach { it.serverStop() }
    }

    companion object {
        private fun load(tag: CompoundTag, level: ServerLevel): CellGraphManager {
            val manager = CellGraphManager(level)

            val graphListTag = tag.get("Graphs") as ListTag?

            if (graphListTag == null) {
                LOG.info("No nodes to be loaded!")
                return manager
            }

            graphListTag.forEach { circuitNbt ->
                val graphCompound = circuitNbt as CompoundTag
                val graph = CellGraph.fromNbt(graphCompound, manager, level)

                if (graph.isEmpty()) {
                    LOG.error("Loaded circuit with no cells!")
                    return@forEach
                }

                manager.addGraph(graph)

                LOG.info("Loaded ${graph.size} cells for ${graph.id}!")
            }

            manager.graphs.values.forEach {
                it.forEach { cell ->
                    cell.onWorldLoadedPreSolver()
                }
            }

            manager.graphs.values.forEach { it.buildSolver() }

            manager.graphs.values.forEach {
                it.forEach { cell ->
                    cell.onWorldLoadedPostSolver()
                }
            }

            manager.graphs.values.forEach {
                it.forEach { cell ->
                    cell.onWorldLoadedPreSim()
                }
            }

            manager.graphs.values.forEach {
                it.startSimulation()
            }

            manager.graphs.values.forEach {
                it.forEach { cell ->
                    cell.onWorldLoadedPostSim()
                }
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
abstract class CellProvider<T : Cell> {
    /**
     * Gets the resource ID of this cell.
     * */
    val id get() = CellRegistry.getCellId(this)

    /**
     * Creates a new instance of the cell.
     * */
    abstract fun create(ci: CellCreateInfo): T

    fun create(locator: Locator, environment: HashDataTable) = create(CellCreateInfo(locator, id, environment))
}

class BasicCellProvider<T : Cell>(val factory: (CellCreateInfo) -> T) : CellProvider<T>() {
    override fun create(ci: CellCreateInfo) = factory(ci)
}

class InjectCellProvider<T : Cell>(val c: Class<T>, val extraParams: List<Any>) : CellProvider<T>() {
    constructor(c: Class<T>) : this(c, listOf())

    @Suppress("UNCHECKED_CAST")
    override fun create(ci: CellCreateInfo) =
        ServiceCollection()
            .withSingleton { ci }
            .withSingleton { this }
            .activate(c, extraParams) as T
}

/**
 * Describes the pin exported to other Electrical Objects.
 * */
const val EXTERNAL_PIN: Int = 1

/**
 * Describes the pin used internally by Electrical Objects.
 * */
const val INTERNAL_PIN: Int = 0
const val POSITIVE_PIN = EXTERNAL_PIN
const val NEGATIVE_PIN = INTERNAL_PIN

/**
 * Generator model consisting of a Voltage Source + Resistor
 * */
open class VRGeneratorObject<C : Cell>(cell: Cell, val map: PoleMap) : ElectricalObject<Cell>(cell) {
    private val resistor = ComponentHolder {
        Resistor().also { it.resistance = resistanceExact }
    }

    private val source = ComponentHolder {
        VoltageSource().also { it.potential = potentialExact }
    }

    /**
     * Gets the exact resistance of the [resistor].
     * */
    var resistanceExact: Double = 1.0
        set(value) {
            field = value
            resistor.ifPresent { it.resistance = value }
        }

    /**
     * Gets the exact potential of the [resistor].
     * */
    var potentialExact: Double = 1.0
        set(value) {
            field = value
            source.ifPresent { it.potential = value }
        }

    /**
     * Updates the resistance if the deviation between the current resistance and [value] is larger than [eps].
     * This should be used instead of setting [resistanceExact] whenever possible, because setting the resistance is expensive.
     * @return True if the resistance was updated. Otherwise, false.
     * */
    fun updateResistance(value: Double, eps: Double = LIBAGE_SET_EPS): Boolean {
        if(resistanceExact.approxEq(value, eps)) {
            return false
        }

        resistanceExact = value

        return true
    }

    /**
     * Updates the potential if the deviation between the current potential and [value] is larger than [eps].
     * Using this instead of setting [potentialExact] doesn't have a large performance impact.
     * @return True if the voltage was updated. Otherwise, false.
     * */
    fun updatePotential(value: Double, eps: Double = LIBAGE_SET_EPS): Boolean {
        if(potentialExact.approxEq(value, eps)) {
            return false
        }

        potentialExact = value

        return true
    }

    val hasResistor get() = resistor.isPresent
    val hasSource get() = source.isPresent

    val resistorCurrent get() = if(resistor.isPresent) resistor.instance.current else 0.0
    val sourceCurrent get() = if(source.isPresent) source.instance.current else 0.0

    val resistorPower get() = if (resistor.isPresent) resistor.instance.power else 0.0
    val sourcePower get() = if (source.isPresent) source.instance.power else 0.0

    override val maxConnections = 2

    /**
     * Gets the offered component by evaluating the map.
     * @return The resistor's external pin when the pole evaluates to *plus*. The source's negative pin when the pole evaluates to *minus*.
     * */
    override fun offerComponent(neighbour: ElectricalObject<*>): ElectricalComponentInfo =
        when (map.evaluate(this.cell.locator, neighbour.cell.locator)) {
            Pole.Plus -> resistor.offerExternal()
            Pole.Minus -> source.offerNegative()
        }

    override fun clearComponents() {
        resistor.clear()
        source.clear()
    }

    override fun addComponents(circuit: Circuit) {
        circuit.add(resistor)
        circuit.add(source)
    }

    override fun build() {
        resistor.connectInternal(source.offerPositive())
        super.build()
    }
}

open class PVSObject<C : Cell>(cell: Cell, val map: PoleMap) : ElectricalObject<Cell>(cell) {
    private val source = ComponentHolder {
        PowerVoltageSource().also {
            it.potentialMax = potentialMaxExact
            it.powerIdeal = powerIdealExact
        }
    }

    var potentialMaxExact: Double = 0.0
        set(value) {
            field = value
            source.ifPresent { it.potentialMax = value }
        }

    var powerIdealExact: Double = 0.0
        set(value) {
            field = value
            source.ifPresent { it.powerIdeal = value }
        }

    fun updatePotentialMax(value: Double, eps: Double = LIBAGE_SET_EPS): Boolean {
        if(potentialMaxExact.approxEq(value, eps)) {
            return false
        }

        potentialMaxExact = value

        return true
    }

    fun updatePowerIdeal(value: Double, eps: Double = LIBAGE_SET_EPS): Boolean {
        if(powerIdealExact.approxEq(value, eps)) {
            return false
        }

        powerIdealExact = value

        return true
    }

    val hasSource get() = source.isPresent
    val sourceCurrent get() = if(source.isPresent) source.instance.current else 0.0
    val sourcePower get() = if (source.isPresent) source.instance.power else 0.0

    override val maxConnections = 2

    /**
     * Gets the offered component by evaluating the map.
     * @return The resistor's external pin when the pole evaluates to *plus*. The source's negative pin when the pole evaluates to *minus*.
     * */
    override fun offerComponent(neighbour: ElectricalObject<*>): ElectricalComponentInfo =
        when (map.evaluate(this.cell.locator, neighbour.cell.locator)) {
            Pole.Plus -> source.offerPositive()
            Pole.Minus -> source.offerNegative()
        }

    override fun clearComponents() {
        source.clear()
    }
}

