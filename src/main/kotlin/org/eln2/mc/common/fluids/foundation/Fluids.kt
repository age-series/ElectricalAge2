package org.eln2.mc.common.fluids.foundation

import net.minecraft.resources.ResourceLocation
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions
import net.minecraftforge.fluids.FluidType
import org.eln2.mc.client.render.foundation.McColor
import java.util.function.Consumer

val WATER_SOURCE_TEXTURE = ResourceLocation("block/water_still")
val WATER_FLOW_TEXTURE = ResourceLocation("block/water_flow")
val WATER_OVERLAY_TEXTURE = ResourceLocation("block/water_overlay")

class BasicFluidType(
    properties: Properties? = null,
    val source: ResourceLocation,
    val flow: ResourceLocation,
    val overlay: ResourceLocation? = null,
    val tint: McColor,
) : FluidType(properties) {
    override fun initializeClient(consumer: Consumer<IClientFluidTypeExtensions?>) {
        consumer.accept(object : IClientFluidTypeExtensions {
            override fun getStillTexture() = source
            override fun getFlowingTexture() = flow
            override fun getOverlayTexture() = overlay
            override fun getTintColor() = tint.value
        })
    }
}
