package org.eln2.mc.common

import net.minecraft.core.Direction

enum class RelativeRotationDirection {
    Front,
    Back,
    Left,
    Right,
}

class PlacementRotation(val placementDirection : Direction) {
    private val relativeToAbsolute = HashMap(RelativeRotationDirection.values().associateWith { getAbsoluteFromRelative(it) })
    private val absoluteToRelative = relativeToAbsolute.entries.associateBy({ it.value }){ it.key }

    fun getAbsoluteFromRelative(rotation : RelativeRotationDirection) : Direction {
        return when(rotation){
            RelativeRotationDirection.Front -> placementDirection
            RelativeRotationDirection.Back -> placementDirection.opposite
            RelativeRotationDirection.Right -> placementDirection.clockWise
            RelativeRotationDirection.Left -> placementDirection.counterClockWise
        }
    }

    fun getRelativeFromAbsolute(direction: Direction) : RelativeRotationDirection {
        return absoluteToRelative[direction]!!
    }
}

