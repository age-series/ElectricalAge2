package org.eln2.mc.common.parts.part

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.eln2.mc.client.render.BasicPartRenderer
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.common.RelativeRotationDirection
import org.eln2.mc.common.cell.CellRegistry
import org.eln2.mc.common.parts.CellPart
import org.eln2.mc.common.parts.ConnectionMode
import org.eln2.mc.common.parts.IPartRenderer
import org.eln2.mc.common.parts.PartPlacementContext

class BatteryPart(id : ResourceLocation, context : PartPlacementContext) : CellPart(id, context, CellRegistry.`12V_BATTERY_CELL`.get()) {
    override val allowInnerConnections = false
    override val baseSize: Vec3
        get() = Vec3(0.7, 0.6, 0.7)

    override fun createRenderer(): IPartRenderer {
        val renderer = BasicPartRenderer(this, PartialModels.BATTERY)

        renderer.downOffset = 0.4

        return renderer
    }

    override fun recordConnection(direction: RelativeRotationDirection, mode: ConnectionMode) { }

    override fun recordDeletedConnection(direction: RelativeRotationDirection) { }
}
