package org.eln2.mc.common.parts.providers

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import org.eln2.mc.common.parts.Part
import org.eln2.mc.common.parts.PartProvider

class MyPartProvider(val factory : ((pos : BlockPos, face : Direction) -> Part)) : PartProvider() {
    override fun create(pos : BlockPos, face : Direction): Part {
        return factory(pos, face)
    }
}
