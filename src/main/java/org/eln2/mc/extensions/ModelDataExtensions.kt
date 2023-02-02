package org.eln2.mc.extensions

import com.jozufozu.flywheel.core.structs.model.ModelData
import com.mojang.math.Quaternion
import net.minecraft.world.phys.Vec3
import org.eln2.mc.common.parts.Part

object ModelDataExtensions {
    fun ModelData.applyPartTransform(part : Part, rotation : Quaternion, size : Vec3, offset : Vec3) : ModelData{
        return this.loadIdentity()
            .translate(part.worldBoundingBox.center)
            .multiply(part.placementContext.face.rotation)
            .translate(offset)
            .multiply(rotation)
            .scale(size.x.toFloat(), size.y.toFloat(), size.z.toFloat())
    }

    fun ModelData.applyPartTransform(part : Part) : ModelData{
        return this.applyPartTransform(
            part,
            rotation = part.facingRotation,
            size = part.baseSize,
            offset = Vec3(-0.5, -part.baseSize.y / 2, -0.5))
    }
}
