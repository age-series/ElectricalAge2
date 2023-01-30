package org.eln2.mc.common.parts.part

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import org.eln2.mc.Eln2
import org.eln2.mc.client.render.PartialModelPartRenderer
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.common.RelativeRotationDirection
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.CellRegistry
import org.eln2.mc.common.parts.*

class MyPart(id : ResourceLocation, context : PartPlacementContext) : CellPart(id, context, CellRegistry.WIRE_CELL.get()) {
    override val baseSize: Vec3
        get() = Vec3(0.5, 0.25, 0.5)

    override fun onUsedBy(entity: LivingEntity) {
        Eln2.LOGGER.info("Test part used by $entity")
    }

    override fun createRenderer(): IPartRenderer {
        return PartialModelPartRenderer(this, PartialModels.MY_MODEL)
    }

    override val allowPlanarConnections = true
    override val allowInnerConnections = true
    override val allowWrappedConnections: Boolean
        get() = TODO("Not yet implemented")
}
