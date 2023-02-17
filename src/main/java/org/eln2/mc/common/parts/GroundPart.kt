package org.eln2.mc.common.parts

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.ConnectionMode
import org.eln2.mc.common.parts.foundation.IPartRenderer
import org.eln2.mc.common.parts.foundation.PartPlacementContext
import org.eln2.mc.common.space.RelativeRotationDirection

class GroundPart(id: ResourceLocation, placementContext: PartPlacementContext)
    : CellPart(id, placementContext, CellRegistry.GROUND_CELL.get()) {
    override val baseSize = Vec3(1.0, 1.0, 1.0)
    override fun createRenderer(): IPartRenderer {
        return BasicPartRenderer(this, PartialModels.WIRE_CROSSING_EMPTY)
    }

    override fun recordConnection(direction: RelativeRotationDirection, mode: ConnectionMode) {}

    override fun recordDeletedConnection(direction: RelativeRotationDirection) {}
}
