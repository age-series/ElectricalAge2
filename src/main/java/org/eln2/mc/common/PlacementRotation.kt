package org.eln2.mc.common

import net.minecraft.core.Direction

enum class RelativeRotationDirection {
    Front,
    Back,
    Left,
    Right,
    Up,
    Down
}

class PlacementRotation(val placementDirection : Direction) {
    fun getAbsoluteFromRelative(rotation : RelativeRotationDirection) : Direction {
        return when(rotation){
            RelativeRotationDirection.Front -> placementDirection
            RelativeRotationDirection.Back -> placementDirection.opposite
            RelativeRotationDirection.Right -> placementDirection.clockWise
            RelativeRotationDirection.Left -> placementDirection.counterClockWise
            RelativeRotationDirection.Up -> Direction.UP
            RelativeRotationDirection.Down -> Direction.DOWN
        }
    }

    fun getRelativeFromAbsolute(direction: Direction) : RelativeRotationDirection {
        return when(direction){
            placementDirection -> RelativeRotationDirection.Front
            placementDirection.opposite -> RelativeRotationDirection.Back
            placementDirection.clockWise -> RelativeRotationDirection.Right
            placementDirection.counterClockWise -> RelativeRotationDirection.Left
            Direction.UP -> RelativeRotationDirection.Up
            Direction.DOWN -> RelativeRotationDirection.Down
            else -> error("Direction not implemented: $direction")
        }
    }
}

