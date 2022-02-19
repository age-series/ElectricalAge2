package org.eln2.mc.common

import net.minecraft.core.Direction

enum class RelativeRotationDirection {
    Front,
    Back,
    Left,
    Right,
}

class PlacementRotation(val placementDirection : Direction) {
    fun getAbsoluteFromRelative(rotation : RelativeRotationDirection) : Direction {
        return when(rotation){
            RelativeRotationDirection.Front -> placementDirection
            RelativeRotationDirection.Back -> placementDirection.opposite
            RelativeRotationDirection.Right -> placementDirection.clockWise
            RelativeRotationDirection.Left -> placementDirection.counterClockWise
        }
    }

    fun getRelativeFromAbsolute(direction: Direction) : RelativeRotationDirection {
        return when(direction){
            placementDirection -> RelativeRotationDirection.Front
            placementDirection.opposite -> RelativeRotationDirection.Back
            placementDirection.clockWise -> RelativeRotationDirection.Right
            placementDirection.counterClockWise -> RelativeRotationDirection.Left
            else -> error("Direction not implemented: $direction")
        }
    }
}

