package org.eln2.mc.common.cell

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import org.eln2.mc.common.*

abstract class CellBase(val pos : BlockPos) {
    // used to store the cell type on the disk using the graph manager, null before setId
    private var _id : ResourceLocation? = null
    val id get() = _id!!

    @In(Side.LogicalServer)
    fun setId(id : ResourceLocation){
        _id = id
    }

    /**
     * Called when the graph manager completed loading this cell from the disk.
    */
    abstract fun completeDiskLoad()

    /**
     *   Called when the tile entity was placed.
     *   @param currentConnections The adjacent cells that are connected.
    */
    @In(Side.LogicalServer)
    abstract fun setPlaced(currentConnections : ArrayList<CellBase>)

    /**
     *  Called when the neighbouring cells update.
     *  @param connections The connections after the neighbours updated.
    */
    @In(Side.LogicalServer)
    abstract fun setConnections(connections : ArrayList<CellBase>)

    /**
     *  Called when the graph is being rebuilt. E.G when a removed cell split the graph.
     *  @param newGraph The new graph we are part of.
    */
    @In(Side.LogicalServer)
    abstract fun setGraph(newGraph: CellGraph)

    /**
     *  Called when the cell is being moved to a new graph or when the cell is being loaded from the disk
     *  @param newGraph The new graph.
     *  @param connections The connections after the update.
    */
    @In(Side.LogicalServer)
    abstract fun setGraphAndConnections(newGraph : CellGraph, connections : ArrayList<CellBase>)

    /**
     * Called when the tile entity is destroyed.
    */
    @In(Side.LogicalServer)
    abstract fun destroy()

    /**
     * At the moment, the graph uses this to store the connection block positions to disk.
    */
    @In(Side.LogicalServer)
    abstract fun getCurrentConnections() : ArrayList<CellBase>

    @In(Side.LogicalServer)
    abstract fun getCurrentGraph() : CellGraph
}
