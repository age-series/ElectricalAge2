package org.eln2.mc.common.parts.providers

import net.minecraft.resources.ResourceLocation
import org.eln2.mc.common.parts.Part
import org.eln2.mc.common.parts.PartPlacementContext
import org.eln2.mc.common.parts.PartProvider

class BasicPartProvider(val factory : ((id : ResourceLocation, context : PartPlacementContext) -> Part)) : PartProvider() {
    override fun create(context: PartPlacementContext): Part {
        return factory(id, context)
    }
}
