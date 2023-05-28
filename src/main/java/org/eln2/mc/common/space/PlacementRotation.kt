package org.eln2.mc.common.space

import com.mojang.math.Matrix4f
import net.minecraft.core.Direction
import org.eln2.mc.extensions.isVertical

/**
 * The Relative Rotation Direction represents a direction relative to an object's frame.
 * */
enum class RelativeDir(val id: Int) {
    Front(1),
    Back(2),
    Left(3),
    Right(4),
    Up(5),
    Down(6);

    /**
     * Gets the opposite of this direction.
     * */
    val opposite
        get() = when (this) {
            Front -> Back
            Back -> Front
            Left -> Right
            Right -> Left
            Up -> Down
            Down -> Up
        }

    /**
     * @return True, if this direction is a horizontal direction.
     * */
    val isHorizontal get() = this != Up && this != Down

    /**
     * @return True, if this direction is a vertical direction.
     * */
    val isVertical get() = this == Up || this == Down

    companion object {
        /**
         * Computes the Relative Rotation Direction from a global direction.
         * @param facing The forward direction of the object.
         * @param normal The up direction of the object.
         * @param direction The global direction.
         * @return The global direction, mapped to the relative direction, in the object's frame.
         * */
        fun fromForwardUp(facing: Direction, normal: Direction, direction: Direction): RelativeDir {
            if (facing.isVertical()) {
                error("Facing cannot be vertical")
            }

            if (direction == normal) {
                return Up
            }

            if (direction == normal.opposite) {
                return Down
            }

            val adjustedFacing = Direction.rotate(Matrix4f(normal.rotation), facing)

            var result = when (direction) {
                adjustedFacing -> Front
                adjustedFacing.opposite -> Back
                adjustedFacing.getClockWise(normal.axis) -> Right
                adjustedFacing.getCounterClockWise(normal.axis) -> Left
                else -> error("Adjusted facing did not match")
            }

            if (normal.axisDirection == Direction.AxisDirection.NEGATIVE) {
                if (result == Left || result == Right) {
                    result = result.opposite
                }
            }

            return result
        }

        fun fromId(id: Int): RelativeDir {
            return when (id) {
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
