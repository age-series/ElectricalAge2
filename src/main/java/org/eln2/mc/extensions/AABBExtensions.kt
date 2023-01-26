package org.eln2.mc.extensions

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.eln2.mc.extensions.Vec3Extensions.plus
import org.eln2.mc.extensions.Vec3Extensions.times
import java.util.Optional

object AABBExtensions {
    //todo remove
    fun AABB.viewClip(entity : LivingEntity) : Optional<Vec3>{
        val viewDirection = entity.lookAngle

        val start = Vec3(entity.x, entity.eyeY, entity.z)

        val distance = 5.0

        val end = start + viewDirection * distance;

        return this.clip(start, end)
    }
}
