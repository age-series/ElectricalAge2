package org.eln2.mc.common.cells.foundation

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.ResourceLocation
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.eln2.mc.Eln2
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.common.cells.foundation.objects.SimulationObjectType
import org.eln2.mc.common.space.RelativeRotationDirection
import org.eln2.mc.extensions.NbtExtensions.getCellPos
import org.eln2.mc.extensions.NbtExtensions.getRelativeDirection
import org.eln2.mc.extensions.NbtExtensions.putCellPos
import org.eln2.mc.extensions.NbtExtensions.putRelativeDirection
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.system.measureNanoTime

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

    /**
     * True, if the solution was found last tick. Otherwise, false.
     * */
    var successful = false
        private set

    /**
     * Gets the last tick time (in nanoseconds).
     * */
    var latestSolveTime = 0L
        private set

    fun update() {
        latestSolveTime = measureNanoTime {
            successful = true

            circuits.forEach {
                successful = successful && it.step(0.05)
            }
        }
    }

    /**
     * This realizes the object subsets and creates the underlying simulations.
     * */
    fun buildSolver() {
        cells.forEach { it.clearObjectConnections() }
        cells.forEach { it.recordObjectConnections() }
        cells.forEach { it.build() }

        realizeElectrical()
    }

    /**
     * This method realizes the electrical circuits for all cells that have electrical object.
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
     * */
    fun removeCell(cell: CellBase) {
        cells.remove(cell)
        posCells.remove(cell.pos)
        manager.setDirty()
    }

    /**
     * Adds a cell to the internal sets, assigns its graph, and invalidates the saved data.
     * **This does not update the solver!
     * It is assumed that multiple operations of this type will be performed, then,
     * the solver update will occur explicitly.**
     * */
    fun addCell(cell: CellBase) {
        cells.add(cell)
        cell.graph = this
        posCells[cell.pos] = cell
        manager.setDirty()
    }

    /**
     * Copies the cells of this graph to the other graph, and invalidates the saved data.
     * */
    fun copyTo(graph: CellGraph) {
        graph.cells.addAll(cells)
        manager.setDirty()
    }

    /**
     * Removes the graph from tracking and invalidates the saved data.
     * */
    fun destroy() {
        manager.removeGraph(this)
        manager.setDirty()
    }

    fun toNbt(): CompoundTag {
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

        return circuitCompound
    }

    private data class ConnectionInfoCell(val cellPos: CellPos, val direction: RelativeRotationDirection)

    companion object {
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

            return result
        }
    }
}
