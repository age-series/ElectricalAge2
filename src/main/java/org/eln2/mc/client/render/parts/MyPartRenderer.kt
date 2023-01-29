package org.eln2.mc.client.render.parts

import com.jozufozu.flywheel.core.BasicModelSupplier
import com.jozufozu.flywheel.core.model.BlockMesh
import com.jozufozu.flywheel.core.structs.FlatLit
import com.jozufozu.flywheel.core.structs.StructTypes
import com.jozufozu.flywheel.core.structs.model.ModelData
import net.minecraft.core.Direction
import org.eln2.mc.client.flywheel.instances.MultipartBlockEntityInstance
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.common.parts.IPartRenderer
import org.eln2.mc.common.parts.part.MyPart
import org.eln2.mc.extensions.Vec3Extensions.div
import org.eln2.mc.extensions.Vec3Extensions.unaryMinus

class MyPartRenderer(val part : MyPart) : IPartRenderer {
    companion object{
        val modelSupplier = BasicModelSupplier { BlockMesh(PartialModels.MY_MODEL) }
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
            .multiply(part.placementContext.face.rotation)
            .multiply(part.facingRotation)
            .translate(-part.baseSize / 2.0)
            .scale(part.baseSize.x.toFloat(), part.baseSize.y.toFloat(), part.baseSize.z.toFloat())
    }

    override fun beginFrame() {
    }

    override fun relightModels(): List<FlatLit<*>> {
        return listOf(modelInstance)
    }

    override fun remove() {
        modelInstance.delete()
    }
}
