package org.eln2.mc.client.render

import com.jozufozu.flywheel.core.BasicModelSupplier
import com.jozufozu.flywheel.core.PartialModel
import com.jozufozu.flywheel.core.model.BlockMesh
import com.jozufozu.flywheel.core.structs.FlatLit
import com.jozufozu.flywheel.core.structs.StructTypes
import com.jozufozu.flywheel.core.structs.model.ModelData
import org.eln2.mc.client.flywheel.instances.MultipartBlockEntityInstance
import org.eln2.mc.common.parts.IPartRenderer
import org.eln2.mc.common.parts.Part
import org.eln2.mc.extensions.Vec3Extensions.div
import org.eln2.mc.extensions.Vec3Extensions.unaryMinus

open class PartialModelPartRenderer(val part : Part, val model : PartialModel) : IPartRenderer {
    private val modelSupplier = BasicModelSupplier { BlockMesh(model) }

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

    override fun beginFrame(){}

    override fun relightModels(): List<FlatLit<*>> {
        return listOf(modelInstance)
    }

    override fun remove() {
        modelInstance.delete()
    }
}
