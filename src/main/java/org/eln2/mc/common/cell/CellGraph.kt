package org.eln2.mc.common.cell

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.ResourceLocation
import org.apache.logging.log4j.LogManager
import org.eln2.mc.extensions.NbtExtensions.getBlockPos
import org.eln2.mc.extensions.NbtExtensions.putBlockPos
import java.util.*
import kotlin.collections.HashMap

// the manager is required for marking changes!
class CellGraph(val id : UUID, val manager : CellGraphManager) {
    private val _cells = ArrayList<CellBase>()
    private val _posCells = HashMap<BlockPos, CellBase>()

    val cells get() = _cells

    fun clear() {
        cells.clear()
        _posCells.clear()
        manager.setDirty()
    }

    fun getCellAt(pos: BlockPos): CellBase {
        return _posCells[pos] ?: throw Exception("Could not find cell at $pos!")
    }

    fun containsCell(cell : CellBase) : Boolean{
        return _cells.contains(cell)
    }

    fun removeCell(cell : CellBase) {
        _cells.remove(cell)
        _posCells.remove(cell.pos)
        manager.setDirty()
    }

    fun addCell(cell : CellBase) {
        _cells.add(cell)
        _posCells[cell.pos] = cell
        manager.setDirty()
    }

    fun copyTo(graph : CellGraph){
        graph._cells.addAll(_cells)
        manager.setDirty()
    }

    fun destroy() {
        manager.removeGraph(this)
        manager.setDirty()
    }

    fun serializeNbt() : CompoundTag {
        val circuitCompound = CompoundTag()
        circuitCompound.putUUID("ID", id)

        val cellListTag = ListTag()

        cells.forEach{ cell ->
            val cellTag = CompoundTag()
            val connectionsTag = ListTag()

            cell.getCurrentConnections().forEach { conn->
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
        private val LOGGER = LogManager.getLogger()

        fun deserializeNbt(graphCompound : CompoundTag, manager : CellGraphManager) : CellGraph {
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

                cell.setId(cellId)
                result.addCell(cell)
            }

            // now assign all connections and the graph

            cellConnections.forEach{
                val cell = it.component1()
                val connectionPositions = it.component2()
                val connections = ArrayList(connectionPositions.map { pos -> result.getCellAt(pos) })

                // now set graph and connection
                cell.setGraphAndConnections(result, connections)
                cell.completeDiskLoad()
            }

            return result
        }
    }
}
