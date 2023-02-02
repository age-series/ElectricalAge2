package org.eln2.mc.client.render

import com.jozufozu.flywheel.core.BasicModelSupplier
import com.jozufozu.flywheel.core.model.BlockMesh
import com.jozufozu.flywheel.core.structs.FlatLit
import com.jozufozu.flywheel.core.structs.StructTypes
import com.jozufozu.flywheel.core.structs.model.ModelData
import com.mojang.math.Quaternion
import net.minecraft.world.phys.Vec3
import org.eln2.mc.client.flywheel.instances.MultipartBlockEntityInstance
import org.eln2.mc.common.parts.IPartRenderer
import org.eln2.mc.common.parts.part.WirePart
import org.eln2.mc.extensions.ModelDataExtensions.applyPartTransform

class WirePartRenderer(val part : WirePart) : IPartRenderer {
    companion object{
        val WIRE_CROSSING_EMPTY_SUPPLIER = BasicModelSupplier { BlockMesh(PartialModels.WIRE_CROSSING_EMPTY) }
        val WIRE_CROSSING_SINGLE_WIRE_SUPPLIER = BasicModelSupplier { BlockMesh(PartialModels.WIRE_CROSSING_SINGLE_WIRE) }
        val WIRE_STRAIGHT_SUPPLIER = BasicModelSupplier { BlockMesh(PartialModels.WIRE_STRAIGHT) }
        val WIRE_CORNER_SUPPLIER = BasicModelSupplier { BlockMesh(PartialModels.WIRE_CORNER) }
        val WIRE_CROSSING_FULL_SUPPLIER = BasicModelSupplier { BlockMesh(PartialModels.WIRE_CROSSING_FULL) }
    }

    private lateinit var multipartInstance : MultipartBlockEntityInstance
    private var modelInstance : ModelData? = null

    override fun setupRendering(multipart: MultipartBlockEntityInstance) {
        multipartInstance = multipart

        replaceModel(WIRE_CROSSING_EMPTY_SUPPLIER, Quaternion.ONE)
    }

    private fun replaceModel(supplier: BasicModelSupplier, rotation : Quaternion){
        modelInstance?.delete()

        modelInstance = multipartInstance.instancerManager
            .factory(StructTypes.MODEL)
            .model(supplier)
            .createInstance()
            .applyPartTransform(
                part,
                rotation,
                size = Vec3(1.0, 1.0, 1.0),        // Ignore collider size
                offset = Vec3(-0.5, -0.125, -0.5))  // Translate to block center
    }

    override fun beginFrame(){}

    override fun relightModels(): List<FlatLit<*>> {
        if(modelInstance != null){
            return listOf(modelInstance!!)
        }

        return listOf()
    }

    override fun remove() {
        modelInstance?.delete()
    }
}
