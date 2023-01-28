package org.eln2.mc.client.render.parts

import com.jozufozu.flywheel.api.material.Material
import com.jozufozu.flywheel.core.BasicModelSupplier
import com.jozufozu.flywheel.core.hardcoded.ModelPart
import com.jozufozu.flywheel.core.material.MaterialShaders
import com.jozufozu.flywheel.core.structs.StructTypes
import com.jozufozu.flywheel.core.structs.model.ModelData
import com.jozufozu.flywheel.core.structs.oriented.OrientedData
import com.jozufozu.flywheel.util.transform.TransformStack
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.Sheets
import net.minecraft.world.phys.Vec3
import org.eln2.mc.Eln2
import org.eln2.mc.client.flywheel.instances.MultipartBlockEntityInstance
import org.eln2.mc.common.parts.IPartRenderer
import org.eln2.mc.common.parts.part.MyPart
import org.eln2.mc.extensions.Vec3Extensions.div
import org.eln2.mc.extensions.Vec3Extensions.plus
import org.eln2.mc.extensions.Vec3Extensions.times
import org.eln2.mc.extensions.Vec3Extensions.toVec3
import org.eln2.mc.extensions.Vec3Extensions.unaryMinus

class MyPartRenderer(val part : MyPart) : IPartRenderer {
    companion object{
        val modelSupplier = BasicModelSupplier({
            ModelPart.builder("my_model", 64, 64)
                .cuboid()
                    .size(16f, 16f, 16f)
                .endCuboid()
                .build()
        },
            Material(
                Sheets.solidBlockSheet(),
                MaterialShaders.SHADED_VERTEX,
                MaterialShaders.DEFAULT_FRAGMENT)
        )
    }

    private lateinit var multipartInstance : MultipartBlockEntityInstance
    private lateinit var modelInstance : ModelData

    override fun setupRendering(multipart: MultipartBlockEntityInstance) {
        multipartInstance = multipart

        modelInstance = multipartInstance.instancerManager
            .factory(StructTypes.MODEL)
            .model(modelSupplier)
            .createInstance()
            .loadIdentity()
            .translate(part.worldBoundingBox.center)
            .multiply(part.face.rotation)
            .translate(-part.baseSize / 2.0)
            .scale(part.baseSize.x.toFloat(), part.baseSize.y.toFloat(), part.baseSize.z.toFloat())
    }

    override fun beginFrame() {
    }

    override fun relight() {
        multipartInstance.enqueueRelight(modelInstance)
    }

    override fun remove() {
        modelInstance.delete()
    }
}
