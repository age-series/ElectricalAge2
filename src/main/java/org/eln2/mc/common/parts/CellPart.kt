package org.eln2.mc.common.parts

import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import org.eln2.mc.Eln2
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.CellGraphManager
import org.eln2.mc.common.cell.CellPos
import org.eln2.mc.common.cell.CellProvider
import java.util.UUID

abstract class CellPart(
    id: ResourceLocation,
    placementContext: PartPlacementContext,
    final override val provider : CellProvider) :

    Part(id, placementContext),
    IPartCellContainer {

    final override lateinit var cell: CellBase

    val cellPos = CellPos(placementContext.pos, placementContext.face)

    private lateinit var loadGraphId : UUID

    override fun onPlaced() {
        cell = provider.create(cellPos)
    }

    override fun getSaveTag(): CompoundTag? {
        val tag = CompoundTag()

        tag.putUUID("GraphID", cell.graph.id)

        return tag
    }

    override fun loadFromTag(tag: CompoundTag) {
        if(placementContext.level.isClientSide){
            return
        }

        if(tag.contains("GraphID")){
            loadGraphId = tag.getUUID("GraphID")
        }
        else{
            Eln2.LOGGER.info("Part at $cellPos did not have saved data")
        }
    }

    override fun onLoaded() {
        if(placementContext.level.isClientSide){
            return
        }

        cell = if(!this::loadGraphId.isInitialized){
            Eln2.LOGGER.error("Part cell not initialized!")
            provider.create(cellPos)
        } else{

            Eln2.LOGGER.info("Part loading cell from disk $loadGraphId")

            CellGraphManager
                .getFor(placementContext.level as ServerLevel)
                .getGraphWithId(loadGraphId)
                .getCell(cellPos)
        }
    }
}
