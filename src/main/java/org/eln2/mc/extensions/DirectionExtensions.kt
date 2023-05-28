package org.eln2.mc.extensions

import net.minecraft.core.Direction
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D
import org.eln2.mc.common.space.RelativeDir

fun Direction.isVertical(): Boolean {
    return this == Direction.UP || this == Direction.DOWN
}

fun Direction.isHorizontal(): Boolean {
    return !isVertical()
}

fun Direction.relativeAlias(): RelativeDir {
    return when (this) {
        Direction.DOWN -> RelativeDir.Down
        Direction.UP -> RelativeDir.Up
        Direction.NORTH -> RelativeDir.Front
        Direction.SOUTH -> RelativeDir.Back
        Direction.WEST -> RelativeDir.Left
        Direction.EAST -> RelativeDir.Right
    }
}

fun RelativeDir.directionAlias(): Direction {
    return when (this) {
        RelativeDir.Front -> Direction.NORTH
        RelativeDir.Back -> Direction.SOUTH
        RelativeDir.Left -> Direction.WEST
        RelativeDir.Right -> Direction.EAST
        RelativeDir.Up -> Direction.UP
        RelativeDir.Down -> Direction.DOWN
    }
}

fun Direction.index(): Int {
    return this.get3DDataValue()
}

fun Direction.toVector3D(): Vector3D {
    return Vector3D(this.stepX.toDouble(), this.stepY.toDouble(), this.stepZ.toDouble())
}
