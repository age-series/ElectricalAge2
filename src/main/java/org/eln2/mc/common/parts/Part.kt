package org.eln2.mc.common.parts

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Parts are entity-like units that exist in a multipart entity. They are similar to normal block entities,
 * but up to 6 can exist in the same block space.
 * They are placed on the inner faces of a multipart container block space.
 * */
abstract class Part(val pos : BlockPos, val face : Direction) {
    abstract val size : Double

    /**
     * The local center of the face.
     * */
    private val centerOffset : Vec3 get() {
        val normal = face.normal

        return Vec3(normal.x.toDouble() / 2, normal.y.toDouble() / 2, normal.z.toDouble() / 2)
    }

    /**
     * The world position of the part.
     * */
    private val boundaryPosition : Vec3 get(){
        val offset = centerOffset

        return Vec3(
            pos.x.toDouble() + offset.x,
            pos.y.toDouble() + offset.y,
            pos.z.toDouble() + offset.z)
    }

    val boundingBox : AABB get(){
        val center = boundaryPosition

        val min = Vec3(center.x - size, center.y - size, center.z - size)
        val max = Vec3(center.x + size, center.y + size, center.z + size)

        return  AABB(min, max)
    }

    abstract fun onUsedBy(entity : LivingEntity)
}
