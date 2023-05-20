package org.eln2.mc.client.render.foundation

import com.jozufozu.flywheel.core.materials.model.ModelData
import com.mojang.math.Vector3f
import org.eln2.mc.mathematics.vec3
import org.eln2.mc.common.parts.foundation.Part
import org.eln2.mc.extensions.blockCenter
import org.eln2.mc.extensions.zeroCenter
import org.eln2.mc.extensions.times
import org.eln2.mc.extensions.toVec3

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
