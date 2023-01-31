package org.eln2.mc.common.parts

import net.minecraft.resources.ResourceLocation
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.CellPos
import org.eln2.mc.common.cell.CellProvider

abstract class CellPart(
    id: ResourceLocation,
    placementContext: PartPlacementContext,
    final override val provider : CellProvider) :

    Part(id, placementContext),
    IPartCellContainer {

    final override var cell: CellBase

    val cellPos = CellPos(placementContext.pos, placementContext.face)

    init {
        cell = provider.create(cellPos)
    }
}
