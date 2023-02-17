package org.eln2.mc.common.cells.foundation

import net.minecraft.resources.ResourceLocation
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.common.cells.foundation.objects.ElectricalObject
import org.eln2.mc.common.cells.foundation.objects.SimulationObjectSet
import org.eln2.mc.common.cells.foundation.objects.SimulationObjectType
import org.eln2.mc.common.space.RelativeRotationDirection

data class CellConnectionInfo(val cell: CellBase, val sourceDirection: RelativeRotationDirection)

abstract class CellBase(val pos: CellPos) {
    lateinit var id: ResourceLocation
    lateinit var graph: CellGraph
    lateinit var connections: ArrayList<CellConnectionInfo>

    val hasGraph get() = this::graph.isInitialized

    fun removeConnection(cell : CellBase){
        val target: CellConnectionInfo = connections.firstOrNull { it.cell == cell }
            ?: error("Tried to remove non-existent connection")

        connections.remove(target)
    }

    var container: ICellContainer? = null

    private var createdSet : SimulationObjectSet? = null

    /**
     * Called once when the object set is requested. The result is then cached.
     * @return A new object set, with all the desired objects.
     * */
    abstract fun createObjectSet(): SimulationObjectSet

    private val objectSet : SimulationObjectSet get() {
        if(createdSet == null){
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
    fun clearObjectConnections(){
        objectSet.process { it.clear() }
    }

    /**
     * Called when the solver is being built, in order to record all object-object connections.
     * */
    fun recordObjectConnections(){
        objectSet.process {
            connections.forEach { neighborInfo ->
                if(neighborInfo.cell.hasObject(it.type)){
                    // We can form a connection here.

                    when(it.type){
                        SimulationObjectType.Electrical ->{
                            val localElectrical = it as ElectricalObject
                            val remoteElectrical = neighborInfo.cell.objectSet.electricalObject

                            localElectrical.addConnection(remoteElectrical)
                            remoteElectrical.addConnection(localElectrical)
                        }

                        else -> error("Unhandled simulation object type ${it.type}")
                    }
                }
            }
        }
    }

    /**
     * Called when the solver is being built, in order to finish setting up the underlying components in the
     * simulation objects.
     * */
    fun build(){
        objectSet.process { it.build() }
    }

    fun hasObject(type: SimulationObjectType): Boolean{
        return objectSet.hasObject(type)
    }

    val electricalObject get() = objectSet.electricalObject
}
