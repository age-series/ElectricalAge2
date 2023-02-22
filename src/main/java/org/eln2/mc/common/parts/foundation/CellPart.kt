package org.eln2.mc.common.parts.foundation

import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import org.eln2.mc.Eln2
import org.eln2.mc.annotations.ServerOnly
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellGraphManager
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.CellProvider
import org.eln2.mc.common.space.RelativeRotationDirection
import org.eln2.mc.extensions.NbtExtensions.useSubTagIfPreset
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder
import java.util.*

/**
 * This part represents a simulation object. It can become part of a cell network.
 * */
abstract class CellPart(
    id: ResourceLocation,
    placementContext: PartPlacementContext,
    final override val provider: CellProvider
) :

    Part(id, placementContext),
    IPartCellContainer,
    IWailaProvider {

    companion object {
        private const val GRAPH_ID = "GraphID"
        private const val CUSTOM_SIMULATION_DATA = "SimulationData"
    }

    /**
     * The actual cell contained within this part.
     * It only exists on the server (it is a simulation-only item)
     * */
    @ServerOnly
    final override lateinit var cell: CellBase

    final override val hasCell: Boolean
        get() = this::cell.isInitialized

    private val cellPos = CellPos(placementContext.pos, placementContext.face)

    /**
     * Used by the loading procedures.
     * */
    @ServerOnly
    private lateinit var loadGraphId: UUID

    @ServerOnly
    private var customSimulationData: CompoundTag? = null

    /**
     * Notifies the cell of the new container.
     * */
    override fun onPlaced() {
        cell = provider.create(cellPos)
        cell.container = placementContext.multipart
    }

    /**
     * Notifies the cell that the container has been removed.
     * */
    override fun onUnloaded() {
        if (hasCell) {
            cell.onContainerUnloaded()
            cell.container = null
        }
    }

    /**
     * The saved data includes the Graph ID. This is used to fetch the cell after loading.
     * */
    override fun getSaveTag(): CompoundTag? {
        if (!hasCell) {
            Eln2.LOGGER.error("Saving, but cell not initialized!")
            return null
        }

        val tag = CompoundTag()

        tag.putUUID(GRAPH_ID, cell.graph.id)

        saveCustomSimData()?.also {
            tag.put(CUSTOM_SIMULATION_DATA, it)
        }

        return tag
    }

    /**
     * This method gets the graph ID from the saved data.
     * The level is not available at this point, so we defer cell fetching to the onLoaded method.
     * */
    override fun loadFromTag(tag: CompoundTag) {
        if (placementContext.level.isClientSide) {
            return
        }

        if (tag.contains(GRAPH_ID)) {
            loadGraphId = tag.getUUID("GraphID")
        } else {
            Eln2.LOGGER.info("Part at $cellPos did not have saved data")
        }

        tag.useSubTagIfPreset(CUSTOM_SIMULATION_DATA) { customSimulationData = it }
    }

    /**
     * This is the final stage of loading. We have the level, so we can fetch the cell using the saved data.
     * */
    override fun onLoaded() {
        if (placementContext.level.isClientSide) {
            return
        }

        cell = if (!this::loadGraphId.isInitialized) {
            Eln2.LOGGER.error("Part cell not initialized!")
            // Should we blow up the game?
            provider.create(cellPos)
        } else {
            CellGraphManager
                .getFor(placementContext.level as ServerLevel)
                .getGraph(loadGraphId)
                .getCell(cellPos)
        }

        cell.container = placementContext.multipart
        cell.onContainerLoaded()

        if(customSimulationData != null){
            Eln2.LOGGER.info(customSimulationData)
            loadCustomSimData(customSimulationData!!)
            customSimulationData = null
        }
    }

    open fun saveCustomSimData(): CompoundTag?{
        return null
    }

    open fun loadCustomSimData(tag: CompoundTag) {}

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        if (hasCell) {
            this.cell.appendBody(builder, config)
        }
    }

    override fun recordConnection(direction: RelativeRotationDirection, mode: ConnectionMode) {}

    override fun recordDeletedConnection(direction: RelativeRotationDirection) {}

    override val allowPlanarConnections = true
    override val allowInnerConnections = true
    override val allowWrappedConnections = true
}
