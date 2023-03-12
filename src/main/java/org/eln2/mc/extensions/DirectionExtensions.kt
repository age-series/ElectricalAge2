package org.eln2.mc.extensions

import com.jozufozu.flywheel.repack.joml.Vector3d
import net.minecraft.core.Direction
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D
import org.eln2.mc.common.space.RelativeRotationDirection

object DirectionExtensions {
    fun Direction.isVertical(): Boolean {
        return this == Direction.UP || this == Direction.DOWN
    }

    fun Direction.isHorizontal(): Boolean {
        return !isVertical()
    }

    fun Direction.relativeAlias(): RelativeRotationDirection {
        return when (this) {
            Direction.DOWN -> RelativeRotationDirection.Down
            Direction.UP -> RelativeRotationDirection.Up
            Direction.NORTH -> RelativeRotationDirection.Front
            Direction.SOUTH -> RelativeRotationDirection.Back
            Direction.WEST -> RelativeRotationDirection.Left
            Direction.EAST -> RelativeRotationDirection.Right
        }
    }

    fun RelativeRotationDirection.directionAlias(): Direction {
        return when (this) {
            RelativeRotationDirection.Front -> Direction.NORTH
            RelativeRotationDirection.Back -> Direction.SOUTH
            RelativeRotationDirection.Left -> Direction.WEST
            RelativeRotationDirection.Right -> Direction.EAST
            RelativeRotationDirection.Up -> Direction.UP
            RelativeRotationDirection.Down -> Direction.DOWN
        }
    }

    fun Direction.index(): Int {
        return this.get3DDataValue()
    }

    fun Direction.toVector3d(): Vector3d {
        return Vector3d(this.stepX.toDouble(), this.stepY.toDouble(), this.stepZ.toDouble())
    }

    fun Direction.toVector3D(): Vector3D {
        return Vector3D(this.stepX.toDouble(), this.stepY.toDouble(), this.stepZ.toDouble())
    }
}
