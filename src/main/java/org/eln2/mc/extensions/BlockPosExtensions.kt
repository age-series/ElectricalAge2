package org.eln2.mc.extensions

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i


object BlockPosExtensions {
    operator fun BlockPos.plus(displacement : Vec3i) : BlockPos{
        return this.offset(displacement)
    }

    operator fun BlockPos.plus(direction: Direction) : BlockPos{
        return this + direction.normal
    }
}
