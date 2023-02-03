package org.eln2.mc.client.render

import com.jozufozu.flywheel.core.BasicModelSupplier
import com.jozufozu.flywheel.core.model.BlockMesh
import com.jozufozu.flywheel.core.structs.FlatLit
import com.jozufozu.flywheel.core.structs.StructTypes
import com.jozufozu.flywheel.core.structs.model.ModelData
import com.mojang.math.Quaternion
import com.mojang.math.Vector3f
import net.minecraft.world.phys.Vec3
import org.eln2.mc.Eln2
import org.eln2.mc.client.flywheel.instances.MultipartBlockEntityInstance
import org.eln2.mc.common.RelativeRotationDirection
import org.eln2.mc.common.parts.IPartRenderer
import org.eln2.mc.common.parts.part.WirePart
import org.eln2.mc.extensions.DirectionExtensions.isVertical
import org.eln2.mc.extensions.ModelDataExtensions.blockCenter
import org.eln2.mc.extensions.ModelDataExtensions.zeroCenter
import org.eln2.mc.extensions.QuaternionExtensions.times
import org.eln2.mc.extensions.Vec3Extensions.times
import org.eln2.mc.extensions.Vec3Extensions.toVec3

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

    // Reset on every frame
    private var latestDirections : List<RelativeRotationDirection>? = null

    fun applyDirections(directions : List<RelativeRotationDirection>){
        latestDirections = directions

        Eln2.LOGGER.info("dirs: ")

        directions.forEach {
            Eln2.LOGGER.info(it.name)
        }
    }

    override fun setupRendering(multipart: MultipartBlockEntityInstance) {
        multipartInstance = multipart

        updateInstance(WIRE_CROSSING_EMPTY_SUPPLIER, Vector3f.YP.rotationDegrees(0f))
    }

    override fun beginFrame(){
        selectModel()
    }

    private fun selectModel(){
        val directions = latestDirections ?: return
        latestDirections = null

        if(applyStraightLine(directions)){
            return
        }
    }

    private fun applyStraightLine(directions: List<RelativeRotationDirection>) : Boolean{
        fun canLineForm() = directions.size == 2
        fun isFrontCase() = directions.contains(RelativeRotationDirection.Front) && directions.contains(RelativeRotationDirection.Back)
        fun isRightCase() = directions.contains(RelativeRotationDirection.Right) && directions.contains(RelativeRotationDirection.Left)

        if(!canLineForm()){
            Eln2.LOGGER.info("Line cannot form")
            return false
        }

        if(isFrontCase()){
            Eln2.LOGGER.info("Front")
            updateInstance(WIRE_STRAIGHT_SUPPLIER, Vector3f.YP.rotationDegrees(0f))
            return true
        }

        if(isRightCase()){
            Eln2.LOGGER.info("Back")
            updateInstance(WIRE_STRAIGHT_SUPPLIER, Vector3f.YP.rotationDegrees(90f))
            return true
        }

        return false
    }

    private fun updateInstance(supplier: BasicModelSupplier, rotation : Quaternion){
        modelInstance?.delete()

        // Conversion equation:
        // let S - world size, B - block bench size
        // S = B / 16
        val size = 1.5 / 16

        modelInstance = multipartInstance.instancerManager
            .factory(StructTypes.MODEL)
            .model(supplier)
            .createInstance()
            .loadIdentity()
            .translate(part.placementContext.face.opposite.normal.toVec3() * Vec3(size, size, size))
            .blockCenter()
            .translate(part.worldBoundingBox.center)
            .multiply(part.placementContext.face.rotation * rotation * part.facingRotation)
            .zeroCenter()
    }

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
