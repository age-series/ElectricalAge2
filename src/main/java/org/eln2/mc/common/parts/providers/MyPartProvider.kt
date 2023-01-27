package org.eln2.mc.common.parts.providers

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import org.eln2.mc.common.parts.Part
import org.eln2.mc.common.parts.PartProvider

class MyPartProvider(val factory : ((pos : BlockPos, face : Direction, id : ResourceLocation, level : Level) -> Part)) : PartProvider() {
    override fun create(pos : BlockPos, face : Direction, level : Level): Part {
        return factory(pos, face, id, level)
    }
}
