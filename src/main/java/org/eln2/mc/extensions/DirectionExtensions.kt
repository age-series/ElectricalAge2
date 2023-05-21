package org.eln2.mc.extensions

import net.minecraft.core.Direction
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D
import org.eln2.mc.common.space.RelativeDirection

fun Direction.isVertical(): Boolean {
    return this == Direction.UP || this == Direction.DOWN
}

fun Direction.isHorizontal(): Boolean {
    return !isVertical()
}

fun Direction.relativeAlias(): RelativeDirection {
    return when (this) {
        Direction.DOWN -> RelativeDirection.Down
        Direction.UP -> RelativeDirection.Up
        Direction.NORTH -> RelativeDirection.Front
        Direction.SOUTH -> RelativeDirection.Back
        Direction.WEST -> RelativeDirection.Left
        Direction.EAST -> RelativeDirection.Right
    }
}

fun RelativeDirection.directionAlias(): Direction {
    return when (this) {
        RelativeDirection.Front -> Direction.NORTH
        RelativeDirection.Back -> Direction.SOUTH
        RelativeDirection.Left -> Direction.WEST
        RelativeDirection.Right -> Direction.EAST
        RelativeDirection.Up -> Direction.UP
        RelativeDirection.Down -> Direction.DOWN
    }
}

fun Direction.index(): Int {
    return this.get3DDataValue()
}

fun Direction.toVector3D(): Vector3D {
    return Vector3D(this.stepX.toDouble(), this.stepY.toDouble(), this.stepZ.toDouble())
}
