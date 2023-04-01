package org.eln2.mc.common.parts.foundation

import com.jozufozu.flywheel.core.PartialModel
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.common.cells.foundation.CellProvider


fun interface IRendererFactory {
    fun create(part: Part): IPartRenderer
}

fun basicRenderer(model: PartialModel, downOffset: Double): IRendererFactory {
    return IRendererFactory { part ->
        BasicPartRenderer(part, model).also { it.downOffset = downOffset }
    }
}

class BasicCellPart(
    id: ResourceLocation,
    placementContext: PartPlacementContext,
    override val baseSize: Vec3,
    provider: CellProvider,
    private val rendererFactory: IRendererFactory):

    CellPart(id, placementContext, provider) {

    override fun createRenderer(): IPartRenderer {
        return rendererFactory.create(this)
    }
}
