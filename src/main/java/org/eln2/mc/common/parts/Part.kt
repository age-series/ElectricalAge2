package org.eln2.mc.common.parts

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.eln2.mc.extensions.Vec3Extensions.minus
import org.eln2.mc.extensions.Vec3Extensions.plus

/**
 * Parts are entity-like units that exist in a multipart entity. They are similar to normal block entities,
 * but up to 6 can exist in the same block space.
 * They are placed on the inner faces of a multipart container block space.
 * */
abstract class Part(val pos : BlockPos, val face : Direction, val id : ResourceLocation, val level : Level) {
    abstract val size : Double

    private var cachedCollisionShape : VoxelShape? = null

    open val blockShape : VoxelShape get() {
        if(cachedCollisionShape == null){
            val halfSize = size / 2

            cachedCollisionShape = Shapes.create(-halfSize, -halfSize, -halfSize, halfSize, halfSize, halfSize)
        }

        return cachedCollisionShape!!
    }

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

        val sizeVector = Vec3(size, size, size)

        val min = center - size
        val max = center + size

        return AABB(min, max)
    }

    abstract fun onUsedBy(entity : LivingEntity)

    open fun getCustomTag() : CompoundTag?{
        return null
    }

    open fun useCustomTag(tag : CompoundTag){}

    open fun onDestroyed(){}

    open fun onAddedToClient(){}
}
