package org.eln2.mc.common.cells.foundation

import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.resources.ResourceLocation
import org.eln2.mc.common.cells.foundation.objects.ConnectionInfo
import org.eln2.mc.common.cells.foundation.objects.ElectricalObject
import org.eln2.mc.common.cells.foundation.objects.SimulationObjectSet
import org.eln2.mc.common.cells.foundation.objects.SimulationObjectType
import org.eln2.mc.common.space.RelativeRotationDirection
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder

data class CellConnectionInfo(val cell: CellBase, val sourceDirection: RelativeRotationDirection)

/**
 * The cell is a physical unit, that may participate in multiple simulations. Each simulation will
 * have a Simulation Object associated with it.
 * Cells create connections with other cells, and objects create connections with other objects of the same simulation type.
 * */
abstract class CellBase(val pos: CellPos, val id: ResourceLocation) : IWailaProvider {
    lateinit var graph: CellGraph
    lateinit var connections: ArrayList<CellConnectionInfo>

    val hasGraph get() = this::graph.isInitialized

    fun removeConnection(cell: CellBase) {
        val target: CellConnectionInfo = connections.firstOrNull { it.cell == cell }
            ?: error("Tried to remove non-existent connection")

        connections.remove(target)
    }

    var container: ICellContainer? = null

    private var createdSet: SimulationObjectSet? = null

    /**
     * Called once when the object set is requested. The result is then cached.
     * @return A new object set, with all the desired objects.
     * */
    abstract fun createObjectSet(): SimulationObjectSet

    private val objectSet: SimulationObjectSet
        get() {
            if (createdSet == null) {
                createdSet = createObjectSet()
            }

            return createdSet!!
        }

    /**
     * Called when the tile entity is being unloaded.
     * After this method is called, the field will become null.
     */
    open fun onEntityUnloaded() {}

    /**
     * Called when the tile entity is being loaded.
     * The field is assigned before this is called.
     */
    open fun onEntityLoaded() {}

    /**
     * Called when the graph manager completed loading this cell from the disk.
     */
    open fun onLoadedFromDisk() {}

    /**
     *   Called when the block entity placing is complete.
     */
    open fun onPlaced() {}

    /**
     * Called when the tile entity is destroyed.
     */
    open fun onDestroyed() {
        objectSet.process { it.destroy() }
    }

    /**
     * Called when the graph and/or neighbouring cells are updated. This method is called after completeDiskLoad and setPlaced
     * @param connectionsChanged True if the neighbouring cells changed.
     * @param graphChanged True if the graph that owns this cell has been updated.
     */
    open fun update(connectionsChanged: Boolean, graphChanged: Boolean) {}

    /**
     * Called when the solver is being built, in order to clear and prepare the objects.
     * */
    fun clearObjectConnections() {
        objectSet.process { it.clear() }
    }

    /**
     * Called when the solver is being built, in order to record all object-object connections.
     * */
    fun recordObjectConnections() {
        objectSet.process {
            connections.forEach { neighborInfo ->
                if (neighborInfo.cell.hasObject(it.type)) {
                    // We can form a connection here.

                    when (it.type) {
                        SimulationObjectType.Electrical -> {
                            val localElectrical = it as ElectricalObject
                            val remoteElectrical = neighborInfo.cell.objectSet.electricalObject

                            localElectrical.addConnection(ConnectionInfo(
                                remoteElectrical,
                                neighborInfo.sourceDirection))
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
        objectSet.process { it.build() }
    }

    /**
     * Checks if this cell has the specified simulation object type.
     * @return True if this cell has the required object. Otherwise, false.
     * */
    fun hasObject(type: SimulationObjectType): Boolean {
        return objectSet.hasObject(type)
    }

    /**
     * Gets the electrical object. Only call if it has been ensured that this cell has an electrical object.
     * */
    val electricalObject get() = objectSet.electricalObject

    /**
     * By default, the cell just passes down the call to objects that implement the WAILA provider.
     * */
    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        // The following 2 calls are debug and will be removed in the future:

        if (hasGraph) {
            builder.text("Graph", graph.id)
        }

        builder.text("Connections", connections.map { it.sourceDirection }.joinToString(" "))

        objectSet.process {
            if (it is IWailaProvider) {
                it.appendBody(builder, config)
            }
        }
    }
}
