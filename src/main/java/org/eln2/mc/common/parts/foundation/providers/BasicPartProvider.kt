package org.eln2.mc.common.parts.foundation.providers

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.eln2.mc.common.parts.foundation.Part
import org.eln2.mc.common.parts.foundation.PartPlacementContext
import org.eln2.mc.common.parts.foundation.PartProvider

open class BasicPartProvider(val factory : ((id : ResourceLocation, context : PartPlacementContext) -> Part),
                             final override val placementCollisionSize: Vec3) : PartProvider() {
    override fun create(context: PartPlacementContext): Part {
        return factory(id, context)
    }
}
