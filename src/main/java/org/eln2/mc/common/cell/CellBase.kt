package org.eln2.mc.common.cell

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import org.eln2.libelectric.sim.electrical.mna.component.Component
import org.eln2.mc.common.In
import org.eln2.mc.common.Side
import org.eln2.mc.common.blocks.CellTileEntity

class ComponentInfo(val component: Component, val index : Int)

abstract class CellBase(val pos : BlockPos) {
    lateinit var id : ResourceLocation
    lateinit var graph: CellGraph
    lateinit var connections : ArrayList<CellBase>
    var tile : CellTileEntity? = null

    /**
     * Called when the tile entity is being unloaded.
     * After this method is called, the field will be null.
    */
    open fun tileUnloaded(){}

    /**
     * Called when the tile entity is being loaded.
     * The field is assigned before this is called.
    */
    open fun tileLoaded(){}

    fun hasGraph() : Boolean { return this::graph.isInitialized }

    /**
     * Called when the graph manager completed loading this cell from the disk.
    */
    @In(Side.LogicalServer)
    open fun completeDiskLoad(){}

    /**
     *   Called when the tile entity placing is complete.
    */
    @In(Side.LogicalServer)
    open fun setPlaced(){}

    /**
     * Called when the tile entity is destroyed.
    */
    @In(Side.LogicalServer)
    open fun destroy() {
        graph.removeCell(this)
    }

    /**
     * Called when the graph and/or neighbouring cells are updated.
    */
    @In(Side.LogicalServer)
    open fun update(connectionsChanged : Boolean, graphChanged : Boolean){}

    open fun createDataPrint() : String{
        return "N/A"
    }

    abstract fun clearForRebuild()
    abstract fun componentForNeighbour(neighbour : CellBase) : ComponentInfo
    abstract fun buildConnections()
}
