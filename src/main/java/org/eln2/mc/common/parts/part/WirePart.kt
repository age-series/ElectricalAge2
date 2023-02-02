package org.eln2.mc.common.parts.part

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.eln2.mc.client.render.WirePartRenderer
import org.eln2.mc.common.cell.CellRegistry
import org.eln2.mc.common.parts.CellPart
import org.eln2.mc.common.parts.IPartRenderer
import org.eln2.mc.common.parts.PartPlacementContext

class WirePart(id : ResourceLocation, context : PartPlacementContext) : CellPart(id, context, CellRegistry.WIRE_CELL.get()) {
    override val baseSize: Vec3
        get() = Vec3(0.5, 0.25, 0.5)

    override fun createRenderer(): IPartRenderer {
        return WirePartRenderer(this)
    }

    override fun onPlaced() {
        super.onPlaced()

        if(!placementContext.level.isClientSide){
            syncChanges()
        }
    }

    override val allowPlanarConnections = true
    override val allowInnerConnections = true
    override val allowWrappedConnections = true
}
