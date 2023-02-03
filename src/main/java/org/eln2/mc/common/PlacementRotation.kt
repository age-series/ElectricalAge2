package org.eln2.mc.common

import com.mojang.math.Matrix4f
import net.minecraft.core.Direction
import org.eln2.mc.extensions.DirectionExtensions.isVertical

enum class RelativeRotationDirection(val id : Int) {
    Front(1),
    Back(2),
    Left(3),
    Right(4),
    Up(5),
    Down(6);

    companion object{
        fun fromFacingUp(facing : Direction, normal : Direction, direction: Direction) : RelativeRotationDirection{
            if(facing.isVertical()){
                error("Facing cannot be vertical")
            }

            if(direction == normal){
                return Up
            }

            if(direction == normal.opposite){
                return Down
            }

            val adjustedFacing = Direction.rotate(Matrix4f(normal.rotation), facing)

            return when(direction){
                adjustedFacing -> Front
                adjustedFacing.opposite -> Back
                adjustedFacing.getClockWise(normal.axis) -> Right
                adjustedFacing.getCounterClockWise(normal.axis) -> Left
                else -> error("Adjusted facing did not match")
            }
        }

        fun fromId(id : Int) : RelativeRotationDirection{
            return when(id){
                Front.id -> Front
                Back.id -> Back
                Left.id -> Left
                Right.id -> Right
                Up.id -> Up
                Down.id -> Down

                else -> error("Invalid local direction id: $id")
            }
        }
    }
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

