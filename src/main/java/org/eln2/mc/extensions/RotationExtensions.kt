package org.eln2.mc.extensions

import net.minecraft.world.level.block.Rotation
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction

fun Rotation.inverse() = when(this) {
    Rotation.NONE -> Rotation.NONE
    Rotation.CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90
    Rotation.CLOCKWISE_180 -> Rotation.CLOCKWISE_180
    Rotation.COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90
}

operator fun Rotation.times(p: BlockPos) = p.rotate(this)

fun rot(dir: Direction) = when(dir) {
    Direction.NORTH -> Rotation.COUNTERCLOCKWISE_90
    Direction.SOUTH -> Rotation.CLOCKWISE_90
    Direction.WEST -> Rotation.CLOCKWISE_180
    Direction.EAST -> Rotation.NONE
    else -> error("Invalid horizontal facing $dir")
}
