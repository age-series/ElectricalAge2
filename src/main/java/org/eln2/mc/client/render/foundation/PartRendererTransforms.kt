package org.eln2.mc.client.render.foundation

import com.jozufozu.flywheel.core.materials.model.ModelData
import com.mojang.math.Vector3f
import net.minecraft.world.phys.Vec3
import org.eln2.mc.Mathematics.vec3
import org.eln2.mc.common.parts.foundation.Part
import org.eln2.mc.extensions.ModelDataExtensions.blockCenter
import org.eln2.mc.extensions.ModelDataExtensions.zeroCenter
import org.eln2.mc.extensions.QuaternionExtensions.times
import org.eln2.mc.extensions.Vec3Extensions.times
import org.eln2.mc.extensions.Vec3Extensions.toVec3

object PartRendererTransforms {
    fun ModelData.applyBlockBenchTransform(part: Part, downOffset: Double, yRotation: Float = 0f): ModelData {
        return this
            .translate(part.placementContext.face.opposite.normal.toVec3() * vec3(downOffset))
            .blockCenter()
            .translate(part.worldBoundingBox.center)
            .multiply(
                part.placementContext.face.rotation *
                    part.facingRotation *
                    Vector3f.YP.rotationDegrees(yRotation))
            .zeroCenter()
    }
}
