package org.eln2.mc.common.parts.foundation

import com.mojang.math.Quaternion
import com.mojang.math.Vector3f
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.eln2.mc.common.space.RelativeRotationDirection
import org.eln2.mc.extensions.transformed
import org.eln2.mc.extensions.div
import org.eln2.mc.utility.BoundingBox

/**
 * The part space APIs, moved here because they were needed before parts are actually created.
 * */
object PartTransformations {
    /**
     * @see Part.modelBoundingBox
     * */
    fun modelBoundingBox(baseSize: Vec3, horizontalFacing: Direction, face: Direction): AABB {
        return BoundingBox
            .fromSize(baseSize)
            .transformed(facingRotation(horizontalFacing))
            .transformed(face.rotation)
            .move(offset(baseSize, face))
    }

    /**
     * @see Part.facingRotation
     * */
    fun facingRotation(horizontalFacing: Direction): Quaternion {
        return Vector3f.YP.rotationDegrees(facingRotationDegrees(horizontalFacing))
    }

    /**
     * @see Part.facingRotationDegrees
     * */
    fun facingRotationDegrees(horizontalFacing: Direction): Float {
        val offset = 0

        return offset + when (horizontalFacing) {
            Direction.NORTH -> 0f
            Direction.SOUTH -> 180f
            Direction.WEST -> 90f
            Direction.EAST -> -90f
            else -> error("Invalid horizontal facing $horizontalFacing")
        }
    }

    /**
     * @see Part.offset
     * */
    fun offset(baseSize: Vec3, face: Direction): Vec3 {
        val halfSize = baseSize / 2.0

        val positiveOffset = halfSize.y
        val negativeOffset = 1 - halfSize.y

        return when (val axis = face.axis) {
            Direction.Axis.X -> Vec3(
                (if (face.axisDirection == Direction.AxisDirection.POSITIVE) positiveOffset else negativeOffset),
                0.5,
                0.5
            )

            Direction.Axis.Y -> Vec3(
                0.5,
                (if (face.axisDirection == Direction.AxisDirection.POSITIVE) positiveOffset else negativeOffset),
                0.5
            )

            Direction.Axis.Z -> Vec3(
                0.5,
                0.5,
                (if (face.axisDirection == Direction.AxisDirection.POSITIVE) positiveOffset else negativeOffset)
            )

            else -> error("Invalid axis $axis")
        }
    }

    /**
     * @see Part.gridBoundingBox
     * */
    fun gridBoundingBox(baseSize: Vec3, horizontalFacing: Direction, face: Direction, pos: BlockPos): AABB {
        return modelBoundingBox(baseSize, horizontalFacing, face).move(pos)
    }

    /**
     * @see Part.worldBoundingBox
     * */
    fun worldBoundingBox(baseSize: Vec3, horizontalFacing: Direction, face: Direction, pos: BlockPos): AABB {
        return gridBoundingBox(baseSize, horizontalFacing, face, pos).move(Vec3(-0.5, 0.0, -0.5))
    }

    /**
     * @see Part.getRelativeDirection
     * */
    fun getRelativeDirection(
        horizontalFacing: Direction,
        face: Direction,
        global: Direction
    ): RelativeRotationDirection {
        return RelativeRotationDirection.fromForwardUp(
            horizontalFacing,
            face,
            global
        )
    }
}
