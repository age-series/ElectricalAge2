package org.eln2.mc.common.cells.foundation

import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import org.eln2.mc.common.cells.foundation.objects.*
import org.eln2.mc.common.space.LocatorRelationRuleSet
import org.eln2.mc.data.DataAccessNode
import org.eln2.mc.data.IDataEntity
import org.eln2.mc.extensions.putSubTag
import org.eln2.mc.extensions.useSubTagIfPreset
import org.eln2.mc.extensions.withSubTagOptional
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder

/**
 * The cell is a physical unit, that may participate in multiple simulations. Each simulation will
 * have a Simulation Object associated with it.
 * Cells create connections with other cells, and objects create connections with other objects of the same simulation type.
 * */
abstract class CellBase(val pos: CellPos, val id: ResourceLocation) : IWailaProvider, IDataEntity {
    val posDescr get() = pos.descriptor

    companion object {
        private const val CELL_DATA = "cellData"
        private const val OBJECT_DATA = "objectData"
    }

    lateinit var graph: CellGraph
    lateinit var connections: ArrayList<CellBase>

    private val ruleSetLazy = lazy { LocatorRelationRuleSet() }
    protected val ruleSet get() = ruleSetLazy.value

    open fun acceptsConnection(remote: CellBase): Boolean {
        return ruleSet.accepts(pos.descriptor, remote.pos.descriptor)
    }

    /**
     * If [hasGraph], [CellGraph.setChanged] is called to ensure the cell data will be saved.
     * */
    fun setChanged(){
        if(hasGraph){
            graph.setChanged()
        }
    }

    val hasGraph get() = this::graph.isInitialized

    fun removeConnection(cell: CellBase) {
        if(!connections.remove(cell)) {
            error("Tried to remove non-existent connection")
        }
    }

    var container: ICellContainer? = null

    private var createdSet: SimulationObjectSet? = null

    private val behaviorsLazy = lazy {
        CellBehaviorContainer(this)
    }

    protected val behaviorsInitialized get() = behaviorsLazy.isInitialized()

    protected val behaviors get() = behaviorsLazy.value

    /**
     * Called once when the object set is requested. The result is then cached.
     * @return A new object set, with all the desired objects.
     * */
    abstract fun createObjectSet(): SimulationObjectSet

    private val objectSet: SimulationObjectSet
        get() {
            if (createdSet == null) {
                createdSet = createObjectSet()

                createdSet!!.process {
                    if(it is IDataEntity) {
                        dataAccessNode.withChild(it.dataAccessNode)
                    }
                }
            }

            return createdSet!!
        }

    fun loadTag(tag: CompoundTag) {
        tag.useSubTagIfPreset(CELL_DATA, this::loadCellData)
        tag.useSubTagIfPreset(OBJECT_DATA, this::loadObjectData)
    }

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
    open fun loadCellData(tag: CompoundTag) { }

    /**
     * Called when the graph is being saved. Custom data should be saved here.
     * */
    open fun saveCellData(): CompoundTag? = null

    private fun saveObjectData(tag: CompoundTag) {
        objectSet.process { obj ->
            if(obj is IPersistentObject) {
                tag.put(obj.type.name, obj.save())
            }
        }
    }

    private fun loadObjectData(tag: CompoundTag) {
        objectSet.process { obj ->
            if(obj is IPersistentObject) {
                obj.load(tag.getCompound(obj.type.name))
            }
        }
    }

    /**
     * Called when the tile entity is being unloaded.
     * After this method is called, the field will become null.
     */
    open fun onContainerUnloaded() {}

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

    fun remove() {
        behaviors.destroy()
        onRemoving()
    }

    /**
     * Called while the cell is being destroyed, just after the simulation was stopped.
     * Subscribers may be cleaned up here.
     * */
    protected open fun onRemoving() {}

    fun destroy() {
        onDestroyed()
    }

    /**
     * Called after the cell was destroyed.
     */
    open fun onDestroyed() {
        objectSet.process { it.destroy() }
    }

    /**
     * Called when the graph and/or neighbouring cells are updated. This method is called after completeDiskLoad and setPlaced
     * @param connectionsChanged True if the neighbouring cells changed.
     * @param graphChanged True if the graph that owns this cell has been updated.
     */
    open fun update(connectionsChanged: Boolean, graphChanged: Boolean) {
        if(connectionsChanged) {
            onConnectionsChanged()
        }

        if(graphChanged) {
            behaviors.changeGraph()
            onGraphChanged()
        }
    }

    /**
     * Called when this cell's connection list changes.
     * */
    open fun onConnectionsChanged() {}

    /**
     * Called when this cell joined another graph.
     * Subscribers may be added here.
     * */
    open fun onGraphChanged() {}

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
        objectSet.process { localObj ->
            connections.forEach { remoteCell ->
                if(!localObj.acceptsRemote(remoteCell.pos.descriptor)) {
                    return@forEach
                }

                if (!remoteCell.hasObject(localObj.type)) {
                    return@forEach
                }

                val remoteObj = remoteCell.objectSet[localObj.type]

                require(remoteCell.connections.contains(this)) { "Mismatched connection set" }

                if(!remoteObj.acceptsRemote(pos.descriptor)) {
                    return@forEach
                }

                // We can form a connection here.

                when (localObj.type) {
                    SimulationObjectType.Electrical -> {
                        (localObj as ElectricalObject).addConnection(
                            remoteCell.objectSet.electricalObject
                        )
                    }

                    SimulationObjectType.Thermal -> {
                        (localObj as ThermalObject).addConnection(
                            remoteCell.objectSet.thermalObject
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
    val thermalObject get() = objectSet.thermalObject

    /**
     * By default, the cell just passes down the call to objects that implement the WAILA provider.
     * */
    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        if (hasGraph) {
            builder.text("Graph", graph.id)
        }

        objectSet.process {
            if (it is IWailaProvider) {
                it.appendBody(builder, config)
            }
        }

        behaviors.process {
            if(it is IWailaProvider) {
                it.appendBody(builder, config)
            }
        }
    }

    override val dataAccessNode: DataAccessNode = DataAccessNode()
}
