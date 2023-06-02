package org.eln2.mc.utility

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.eln2.mc.viewClip
import org.eln2.mc.div
import org.eln2.mc.minus
import org.eln2.mc.plus

object BoundingBox {
    fun fromCenterSize(center: Vec3, size: Vec3): AABB {
        val halfSize = size / 2.0

        return AABB(center - halfSize, center + halfSize)
    }

    fun fromSize(size: Vec3): AABB {
        return fromCenterSize(Vec3(0.0, 0.0, 0.0), size)
    }

    fun fromSize(x: Double, y: Double, z: Double): AABB {
        return fromSize(Vec3(x, y, z))
    }

    fun fromSize(size: Double): AABB {
        return fromSize(size, size, size)
    }

    fun <T> clipScene(entity: LivingEntity, access: ((T) -> AABB), objects: Collection<T>): T? {
        val intersections = LinkedHashMap<Vec3, T>()

        val eyePos = Vec3(entity.x, entity.eyeY, entity.z)

        objects.forEach { obj ->
            val box = access(obj)

            val intersection = box.viewClip(entity)

            if (!intersection.isEmpty) {
                intersections[intersection.get()] = obj
            }
        }

        val entry = intersections.minByOrNull { entry ->
            (eyePos - entry.key).length()
        }

        return entry?.value
    }
}
