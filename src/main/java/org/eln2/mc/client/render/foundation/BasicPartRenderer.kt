package org.eln2.mc.client.render.foundation

import com.jozufozu.flywheel.core.Materials
import com.jozufozu.flywheel.core.PartialModel
import com.jozufozu.flywheel.core.materials.FlatLit
import com.jozufozu.flywheel.core.materials.model.ModelData
import org.eln2.mc.client.render.MultipartBlockEntityInstance
import org.eln2.mc.client.render.foundation.PartRendererTransforms.applyBlockBenchTransform
import org.eln2.mc.common.parts.foundation.IPartRenderer
import org.eln2.mc.common.parts.foundation.Part

/**
 * The basic part renderer is used to render a single partial model.
 * */
open class BasicPartRenderer(val part: Part, val model: PartialModel) : IPartRenderer {
    /**
     * Useful if the model needs to be rotated to match the networked behavior.
     * Alternatively, the model may be rotated in the 3D editor.
     * */
    var yRotation = 0f

    /**
     * Required in order to "place" the model on the mounting surface. Usually, this offset is calculated using information from the 3D editor.
     * */
    var downOffset = 0.0

    protected var modelInstance: ModelData? = null
    protected lateinit var multipart: MultipartBlockEntityInstance

    override fun setupRendering(multipart: MultipartBlockEntityInstance) {
        this.multipart = multipart

        buildInstance()
    }

    fun buildInstance() {
        if (!this::multipart.isInitialized) {
            error("Multipart not initialized!")
        }

        modelInstance?.delete()

        modelInstance = multipart.materialManager
            .defaultSolid()
            .material(Materials.TRANSFORMED)
            .getModel(model)
            .createInstance()
            .loadIdentity()
            .applyBlockBenchTransform(part, downOffset, yRotation)

        multipart.relightPart(part)
    }

    override fun beginFrame() {}

    override fun relightModels(): List<FlatLit<*>>? {
        if (modelInstance != null) {
            return listOf(modelInstance!!)
        }

        return null
    }

    override fun remove() {
        modelInstance?.delete()
    }
}
