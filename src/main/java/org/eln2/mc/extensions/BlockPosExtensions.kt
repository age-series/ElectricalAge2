package org.eln2.mc.extensions

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.world.phys.Vec3


object BlockPosExtensions {
    operator fun BlockPos.plus(displacement : Vec3i) : BlockPos{
        return this.offset(displacement)
    }

    operator fun BlockPos.plus(direction: Direction) : BlockPos{
        return this + direction.normal
    }

    operator fun BlockPos.minus(other: BlockPos) : BlockPos{
        return BlockPos(this.x - other.x, this.y - other.y, this.z - other.z)
    }

    fun BlockPos.toVec3() : Vec3{
        return Vec3(this.x.toDouble(), this.y.toDouble(), this.z.toDouble());
    }

    fun BlockPos.directionTo(other : BlockPos) : Direction? {
        return Direction.fromNormal(this - other)
    }
}
