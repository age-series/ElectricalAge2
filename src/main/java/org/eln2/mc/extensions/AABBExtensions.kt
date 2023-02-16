package org.eln2.mc.extensions

import com.mojang.math.Matrix4f
import com.mojang.math.Quaternion
import com.mojang.math.Vector3f
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.eln2.mc.extensions.Vec3Extensions.minus
import org.eln2.mc.extensions.Vec3Extensions.plus
import org.eln2.mc.extensions.Vec3Extensions.times
import org.eln2.mc.extensions.Vec3Extensions.toVec3
import org.eln2.mc.extensions.Vec3Extensions.toVector3f
import org.eln2.mc.extensions.Vec3Extensions.toVector4f
import org.eln2.mc.extensions.Vector4fExtensions.toVector3f
import org.eln2.mc.utility.Vectors
import java.util.*

object AABBExtensions {
    //todo remove
    fun AABB.viewClip(entity: LivingEntity): Optional<Vec3> {
        val viewDirection = entity.lookAngle

        val start = Vec3(entity.x, entity.eyeY, entity.z)

        val distance = 5.0

        val end = start + viewDirection * distance

        return this.clip(start, end)
    }

    fun AABB.minVec3(): Vec3 {
        return Vec3(this.minX, this.minY, this.minZ)
    }

    fun AABB.maxVec3(): Vec3 {
        return Vec3(this.maxX, this.maxY, this.maxZ)
    }

    fun AABB.size(): Vec3 {
        return this.maxVec3() - this.minVec3()
    }

    fun AABB.corners(list: MutableList<Vec3>) {
        val min = this.minVec3()
        val max = this.maxVec3()

        list.add(min)
        list.add(Vec3(min.x, min.y, max.z))
        list.add(Vec3(min.x, max.y, min.z))
        list.add(Vec3(max.x, min.y, min.z))
        list.add(Vec3(min.x, max.y, max.z))
        list.add(Vec3(max.x, min.y, max.z))
        list.add(Vec3(max.x, max.y, min.z))
        list.add(max)
    }

    fun AABB.corners(): ArrayList<Vec3> {
        val list = ArrayList<Vec3>()

        this.corners(list)

        return list
    }

    /**
     * Transforms the Axis Aligned Bounding Box by the given rotation.
     * This operation does not change the volume for axis aligned transformations.
     * */
    fun AABB.transformed(quaternion: Quaternion): AABB {
        var min = Vector3f(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        var max = Vector3f(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)

        this.corners().forEach {
            val corner = it.toVector3f()

            corner.transform(quaternion)

            min = Vectors.componentMin(min, corner)
            max = Vectors.componentMax(max, corner)
        }

        return AABB(min.toVec3(), max.toVec3())
    }

    fun AABB.transformed(transform: Matrix4f): AABB {
        var min = Vector3f(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        var max = Vector3f(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)

        this.corners().forEach {
            val corner = it.toVector4f(1f)

            corner.transform(transform)

            val coordinates = corner.toVector3f()

            min = Vectors.componentMin(min, coordinates)
            max = Vectors.componentMax(max, coordinates)
        }

        return AABB(min.toVec3(), max.toVec3())
    }
}
