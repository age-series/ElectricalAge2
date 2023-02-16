package org.eln2.mc.client.render.foundation

import com.jozufozu.flywheel.core.Materials
import com.jozufozu.flywheel.core.PartialModel
import com.jozufozu.flywheel.core.materials.FlatLit
import com.jozufozu.flywheel.core.materials.model.ModelData
import com.mojang.math.Vector3f
import net.minecraft.world.phys.Vec3
import org.eln2.mc.client.render.MultipartBlockEntityInstance
import org.eln2.mc.common.parts.foundation.IPartRenderer
import org.eln2.mc.common.parts.foundation.Part
import org.eln2.mc.extensions.ModelDataExtensions.blockCenter
import org.eln2.mc.extensions.ModelDataExtensions.zeroCenter
import org.eln2.mc.extensions.QuaternionExtensions.times
import org.eln2.mc.extensions.Vec3Extensions.times
import org.eln2.mc.extensions.Vec3Extensions.toVec3

class BasicPartRenderer(val part : Part, val model : PartialModel) : IPartRenderer {
    var yRotation = 0f
    var downOffset = 0.0

    private var modelInstance : ModelData? = null
    private lateinit var multipart : MultipartBlockEntityInstance

    override fun setupRendering(multipart: MultipartBlockEntityInstance) {
        this.multipart = multipart

        buildInstance()
    }

    fun buildInstance(){
        if(!this::multipart.isInitialized){
            error("Multipart not initialized!")
        }

        modelInstance?.delete()

        modelInstance = multipart.materialManager
            .defaultSolid()
            .material(Materials.TRANSFORMED)
            .getModel(model)
            .createInstance()
            .loadIdentity()
            .translate(part.placementContext.face.opposite.normal.toVec3() * Vec3(downOffset, downOffset, downOffset))
            .blockCenter()
            .translate(part.worldBoundingBox.center)
            .multiply(part.placementContext.face.rotation * part.facingRotation * Vector3f.YP.rotationDegrees(yRotation))
            .zeroCenter()

        multipart.relightPart(part)
    }

    override fun beginFrame() { }

    override fun relightModels(): List<FlatLit<*>>? {
        if(modelInstance != null){
            return listOf(modelInstance!!)
        }

        return null
    }

    override fun remove() {
        modelInstance?.delete()
    }
}
