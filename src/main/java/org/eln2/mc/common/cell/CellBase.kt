package org.eln2.mc.common.cell

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import org.ageseries.libage.sim.electrical.mna.component.Component
import org.eln2.mc.common.blocks.CellBlockEntity

class ComponentInfo(val component: Component, val index : Int)

abstract class CellBase(val pos : CellPos) {
    lateinit var id : ResourceLocation
    lateinit var graph: CellGraph
    lateinit var connections : ArrayList<CellBase>

    var entity : CellBlockEntity? = null

    /**
     * Called when the tile entity is being unloaded.
     * After this method is called, the field will become null.
    */
    open fun onEntityUnloaded(){}

    /**
     * Called when the tile entity is being loaded.
     * The field is assigned before this is called.
    */
    open fun onEntityLoaded(){}

    fun hasGraph() : Boolean {
        return this::graph.isInitialized
    }

    /**
     * Called when the graph manager completed loading this cell from the disk.
    */
    open fun onLoadedFromDisk(){}

    /**
     *   Called when the block entity placing is complete.
    */
    open fun onPlaced(){}

    /**
     * Called when the tile entity is destroyed.
    */
    open fun onDestroyed(){}

    /**
     * Called when the graph and/or neighbouring cells are updated. This method is called after completeDiskLoad and setPlaced
     * @param connectionsChanged True if the neighbouring cells changed.
     * @param graphChanged True if the graph that owns this cell has been updated.
    */
    open fun update(connectionsChanged : Boolean, graphChanged : Boolean){}

    /**
     * Called to get a map of key value pairs of circuit properties for the WAILA clone and the plotter.
     * @return Map of circuit property (eg, "voltage") and value (eg, "5.0V")
     */
    abstract fun getHudMap(): Map<String, String>

    /**
     * This method is called before the Circuit is being rebuilt.
    */
    abstract fun clear()

    /**
     * This method is used to return the component and the pin for a remote cell to connect to.
    */
    abstract fun getOfferedComponent(neighbour : CellBase) : ComponentInfo

    /**
     * This method is called after each cell's clear method has been called.
     * It must be used to create the circuit's connections.
    */
    abstract fun buildConnections()
}
