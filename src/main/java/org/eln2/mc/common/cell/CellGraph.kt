package org.eln2.mc.common.cell

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.ResourceLocation
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.eln2.mc.Eln2
import org.eln2.mc.extensions.NbtExtensions.getBlockPos
import org.eln2.mc.extensions.NbtExtensions.putBlockPos
import java.util.*
import kotlin.system.measureNanoTime

class CellGraph(val id : UUID, val manager : CellGraphManager) {
    val cells = ArrayList<CellBase>()
    private val posCells = HashMap<BlockPos, CellBase>()

    lateinit var circuit : Circuit
    val hasCircuit get() = this::circuit.isInitialized

    var latestSolveTime = 0L

    fun serverTick(){
        if(hasCircuit){
            var successful : Boolean

            latestSolveTime = measureNanoTime {
                successful = circuit.step(0.05)
            }

            Eln2.LOGGER.info("Tick time: $latestSolveTime success $successful")
        }
    }

    fun build() {
        circuit = Circuit()

        cells.forEach{ cell ->
            cell.clearForRebuild()
        }

        cells.forEach { cell ->
            cell.buildConnections()
        }

        Eln2.LOGGER.info("Built circuit! component count: ${circuit.components.count()}, graph cell count: ${cells.count()}")
    }

    fun getCellAt(pos: BlockPos): CellBase {
        return posCells[pos] ?: throw Exception("Could not find cell at $pos!")
    }

    fun removeCell(cell : CellBase) {
        cells.remove(cell)
        posCells.remove(cell.pos)
        manager.setDirty()
    }

    fun addCell(cell : CellBase) {
        cells.add(cell)
        posCells[cell.pos] = cell
        manager.setDirty()
    }

    fun copyTo(graph : CellGraph){
        graph.cells.addAll(cells)
        manager.setDirty()
    }

    fun destroyAndRemove() {
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

            cell.connections.forEach { conn->
                val connCompound = CompoundTag()
                connCompound.putBlockPos("Position", conn.pos)
                connectionsTag.add(connCompound)
            }

            cellTag.putBlockPos("Position", cell.pos)
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
            val cellConnections = HashMap<CellBase, ArrayList<BlockPos>>()

            cellListTag.forEach{ cellNbt ->
                val cellCompound = cellNbt as CompoundTag
                val pos = cellCompound.getBlockPos("Position")
                val cellId = ResourceLocation.tryParse(cellCompound.getString("ID"))!!

                val connectionPositions = ArrayList<BlockPos>()
                val connectionsTag = cellCompound.get("Connections") as ListTag

                connectionsTag.forEach {
                    val connectionCompound = it as CompoundTag
                    val connectionPos = connectionCompound.getBlockPos("Position")
                    connectionPositions.add(connectionPos)
                }

                val cell = CellRegistry.registry.getValue(cellId)!!.create(pos)

                cellConnections[cell] = connectionPositions

                cell.id = cellId
                result.addCell(cell)
            }

            // now assign all connections and the graph

            cellConnections.forEach{
                val cell = it.component1()
                val connectionPositions = it.component2()
                val connections = ArrayList(connectionPositions.map { pos -> result.getCellAt(pos) })

                // now set graph and connection
                cell.graph = result
                cell.connections = connections
                cell.update(connectionsChanged = true, graphChanged = true)
                cell.completeDiskLoad()
            }

            return result
        }
    }
}
