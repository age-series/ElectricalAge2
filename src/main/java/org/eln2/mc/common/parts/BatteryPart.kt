package org.eln2.mc.common.parts

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionResult
import net.minecraft.world.phys.Vec3
import org.eln2.mc.Eln2
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.common.parts.foundation.*
import org.eln2.mc.common.space.RelativeRotationDirection

class BatteryPart(id: ResourceLocation, context: PartPlacementContext) :
    CellPart(id, context, CellRegistry.`12V_BATTERY_CELL`.get()),
    ITickablePart {

    override val allowInnerConnections = false

    private var ticksRemaining = 0
    private var tickerRegistered = false
    override val baseSize: Vec3 get() = Vec3(0.6, 0.6, 0.725)

    override fun createRenderer(): IPartRenderer {
        val renderer = BasicPartRenderer(this, PartialModels.BATTERY)

        renderer.downOffset = 0.35

        return renderer
    }

    override fun recordConnection(direction: RelativeRotationDirection, mode: ConnectionMode) {}

    override fun recordDeletedConnection(direction: RelativeRotationDirection) {}

    override fun onUsedBy(context: PartUseContext): InteractionResult {
        if (placementContext.level.isClientSide && !tickerRegistered) {
            ticksRemaining = 100
            placementContext.multipart.addTicker(this)
            tickerRegistered = true
        }

        return super.onUsedBy(context)
    }

    override fun tick() {
        Eln2.LOGGER.info("Battery tick: ${ticksRemaining--}")

        if (ticksRemaining == 0) {
            Eln2.LOGGER.info("Removing battery ticker")

            placementContext.multipart.removeTicker(this)
            tickerRegistered = false
        }
    }
}
