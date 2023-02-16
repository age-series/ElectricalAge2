package org.eln2.mc.common.cells.foundation

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.ResourceLocation
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.eln2.mc.Eln2
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.extensions.NbtExtensions.getCellPos
import org.eln2.mc.extensions.NbtExtensions.putCellPos
import java.util.*
import kotlin.system.measureNanoTime

class CellGraph(val id : UUID, val manager : CellGraphManager) {
    val cells = ArrayList<CellBase>()

    private val posCells = HashMap<CellPos, CellBase>()

    lateinit var circuit : Circuit

    private val hasCircuit get() = this::circuit.isInitialized

    var successful = false
        private set

    var latestSolveTime = 0L

    fun update(){
        latestSolveTime = 0

        if(hasCircuit){
            latestSolveTime = measureNanoTime {
                successful = circuit.step(0.05)
            }
        }
    }

    fun buildSolver() {
        circuit = Circuit()

        cells.forEach{ cell ->
            cell.clear()
        }

        cells.forEach { cell ->
            cell.buildConnections()
        }

        Eln2.LOGGER.info("Built circuit! component count: ${circuit.components.count()}, graph cell count: ${cells.count()}")
    }

    fun getCell(pos: CellPos): CellBase {
        val result = posCells[pos]

        if(result == null){
            Eln2.LOGGER.error("Could not get cell at $pos")
            error("")
        }

        return result
    }

    fun removeCell(cell : CellBase) {
        cells.remove(cell)
        posCells.remove(cell.pos)
        manager.setDirty()
    }

    fun addCell(cell : CellBase) {
        cells.add(cell)
        cell.graph = this
        posCells[cell.pos] = cell
        manager.setDirty()
    }

    fun connectCell(cell : CellBase){
        cell.clear()
        cell.buildConnections()
    }

    fun copyTo(graph : CellGraph){
        graph.cells.addAll(cells)
        manager.setDirty()
    }

    fun destroy() {
        manager.removeGraph(this)
        manager.setDirty()
    }

    fun toNbt() : CompoundTag {
        val circuitCompound = CompoundTag()
        circuitCompound.putUUID("ID", id)

        val cellListTag = ListTag()

        cells.forEach{ cell ->
            val cellTag = CompoundTag()
            val connectionsTag = ListTag()

            cell.connections.forEach { connections ->
                val connectionCompound = CompoundTag()
                connectionCompound.putCellPos("Position", connections .pos)
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

    companion object {
        fun fromNbt(graphCompound : CompoundTag, manager : CellGraphManager) : CellGraph {
            val id = graphCompound.getUUID("ID")
            val result = CellGraph(id, manager)
            val cellListTag = graphCompound.get("Cells") as ListTag?
                ?: // no cells are to be loaded
                return result

            // used to assign the connections after all cells have been loaded
            val cellConnections = HashMap<CellBase, ArrayList<CellPos>>()

            cellListTag.forEach{ cellNbt ->
                val cellCompound = cellNbt as CompoundTag
                val pos = cellCompound.getCellPos("Position")
                val cellId = ResourceLocation.tryParse(cellCompound.getString("ID"))!!

                val connectionPositions = ArrayList<CellPos>()
                val connectionsTag = cellCompound.get("Connections") as ListTag

                connectionsTag.forEach {
                    val connectionCompound = it as CompoundTag
                    val connectionPos = connectionCompound.getCellPos("Position")
                    connectionPositions.add(connectionPos)
                }

                val cell = CellRegistry.getProvider(cellId).create(pos)

                cellConnections[cell] = connectionPositions

                cell.id = cellId
                result.addCell(cell)
            }

            // now assign all connections and the graph

            cellConnections.forEach{
                val cell = it.component1()
                val connectionPositions = it.component2()
                val connections = ArrayList(connectionPositions.map { pos -> result.getCell(pos) })

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
