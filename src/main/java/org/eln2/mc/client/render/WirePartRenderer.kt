package org.eln2.mc.client.render

import com.jozufozu.flywheel.core.Materials
import com.jozufozu.flywheel.core.PartialModel
import com.jozufozu.flywheel.core.materials.FlatLit
import com.jozufozu.flywheel.core.materials.model.ModelData
import com.mojang.math.Quaternion
import com.mojang.math.Vector3f
import net.minecraft.world.phys.Vec3
import org.eln2.mc.Eln2
import org.eln2.mc.client.flywheel.instances.MultipartBlockEntityInstance
import org.eln2.mc.client.render.PartialModels.WIRE_CORNER
import org.eln2.mc.client.render.PartialModels.WIRE_CROSSING
import org.eln2.mc.client.render.PartialModels.WIRE_CROSSING_EMPTY
import org.eln2.mc.client.render.PartialModels.WIRE_CROSSING_FULL
import org.eln2.mc.client.render.PartialModels.WIRE_CROSSING_SINGLE_WIRE
import org.eln2.mc.client.render.PartialModels.WIRE_STRAIGHT
import org.eln2.mc.common.RelativeRotationDirection
import org.eln2.mc.common.parts.IPartRenderer
import org.eln2.mc.common.parts.part.WirePart
import org.eln2.mc.extensions.ModelDataExtensions.blockCenter
import org.eln2.mc.extensions.ModelDataExtensions.zeroCenter
import org.eln2.mc.extensions.QuaternionExtensions.times
import org.eln2.mc.extensions.Vec3Extensions.times
import org.eln2.mc.extensions.Vec3Extensions.toVec3
import java.util.concurrent.atomic.AtomicReference

class WirePartRenderer(val part : WirePart) : IPartRenderer {
    private lateinit var multipartInstance : MultipartBlockEntityInstance
    private var modelInstance : ModelData? = null

    // Reset on every frame
    private var latestDirections = AtomicReference<List<RelativeRotationDirection>>()

    fun applyDirections(directions : List<RelativeRotationDirection>){
        latestDirections.set(directions)
    }

    override fun setupRendering(multipart: MultipartBlockEntityInstance) {
        multipartInstance = multipart
    }

    override fun beginFrame(){
        selectModel()
    }

    private fun selectModel(){
        val directions =
            latestDirections.getAndSet(null)
            ?: return

        if(applyEmpty(directions)){
            return
        }

        if(applySingleLine(directions)){
            return
        }

        if(applyStraightLine(directions)){
            return
        }

        if(applyCorner(directions)){
            return
        }

        if(applyCrossing(directions)){
            return
        }

        if(applyFull(directions)){
            return
        }

        Eln2.LOGGER.error("Wire did not handle cases: $directions")
    }

    private fun applyEmpty(directions: List<RelativeRotationDirection>) : Boolean{
        fun isEmptyCase() = directions.isEmpty()

        if(isEmptyCase()){
            updateInstance(WIRE_CROSSING_EMPTY, Quaternion.ONE)
            return true
        }

        return false
    }

    private fun applySingleLine(directions: List<RelativeRotationDirection>) : Boolean{
        fun lineCanForm() = directions.size == 1

        val modelOffset = 0f

        if(lineCanForm()){
            val angle = modelOffset + when(directions[0]){
                RelativeRotationDirection.Front -> 0f
                RelativeRotationDirection.Back -> 179.99f
                RelativeRotationDirection.Left -> 90f
                RelativeRotationDirection.Right -> 270f
                else -> error("Unexpected ${directions[0]} in wire")

            }

            updateInstance(WIRE_CROSSING_SINGLE_WIRE, Vector3f.YP.rotationDegrees(angle))
            return true
        }

        return false
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
            updateInstance(WIRE_STRAIGHT, Vector3f.YP.rotationDegrees(0f))
            return true
        }

        if(isRightCase()){
            updateInstance(WIRE_STRAIGHT, Vector3f.YP.rotationDegrees(90f))
            return true
        }

        return false
    }

    private fun applyCorner(directions: List<RelativeRotationDirection>) : Boolean{
        fun canCornerForm() = directions.size == 2
        fun isFrontLeftCase() = directions.contains(RelativeRotationDirection.Front) && directions.contains(RelativeRotationDirection.Left)
        fun isFrontRightCase() = directions.contains(RelativeRotationDirection.Front) && directions.contains(RelativeRotationDirection.Right)
        fun isBackLeftCase() = directions.contains(RelativeRotationDirection.Back) && directions.contains(RelativeRotationDirection.Left)
        fun isBackRightCase() = directions.contains(RelativeRotationDirection.Back) && directions.contains(RelativeRotationDirection.Right)

        if(!canCornerForm()){
            return false
        }

        val modelOffset = 0f

        if(isFrontLeftCase()){
            updateInstance(WIRE_CORNER, Vector3f.YP.rotationDegrees(0f + modelOffset))
            return true
        }

        if(isFrontRightCase()){
            updateInstance(WIRE_CORNER, Vector3f.YP.rotationDegrees(-90f + modelOffset))
            return true
        }

        if(isBackLeftCase()){
            updateInstance(WIRE_CORNER, Vector3f.YP.rotationDegrees(90f + modelOffset))
            return true
        }

        if(isBackRightCase()){
            updateInstance(WIRE_CORNER, Vector3f.YP.rotationDegrees(180f + modelOffset))
            return true
        }

        return false
    }

    private fun applyCrossing(directions: List<RelativeRotationDirection>) : Boolean{
        fun canCrossingForm() = directions.size == 3
        fun isFrontLeftRightCase() = directions.contains(RelativeRotationDirection.Front) && directions.contains(RelativeRotationDirection.Left) && directions.contains(RelativeRotationDirection.Right)
        fun isRightFrontBackCase() = directions.contains(RelativeRotationDirection.Right) && directions.contains(RelativeRotationDirection.Front) && directions.contains(RelativeRotationDirection.Back)
        fun isBackRightLeftCase() = directions.contains(RelativeRotationDirection.Back) && directions.contains(RelativeRotationDirection.Right) && directions.contains(RelativeRotationDirection.Left)
        fun isLeftBackFrontCase() = directions.contains(RelativeRotationDirection.Left) && directions.contains(RelativeRotationDirection.Back) && directions.contains(RelativeRotationDirection.Front)

        if(!canCrossingForm()){
            return false
        }

        val modelOffset = 0f

        if(isFrontLeftRightCase()){
            updateInstance(WIRE_CROSSING, Vector3f.YP.rotationDegrees(0f + modelOffset))
            return true
        }

        if(isRightFrontBackCase()){
            updateInstance(WIRE_CROSSING, Vector3f.YP.rotationDegrees(-90f + modelOffset))
            return true
        }

        if(isBackRightLeftCase()){
            updateInstance(WIRE_CROSSING, Vector3f.YP.rotationDegrees(-179.99f + modelOffset))
            return true
        }

        if(isLeftBackFrontCase()){
            updateInstance(WIRE_CROSSING, Vector3f.YP.rotationDegrees(90f + modelOffset))
            return true
        }

        return false
    }

    private fun applyFull(directions: List<RelativeRotationDirection>) : Boolean{
        fun canFullCrossingForm() = directions.size == 4
        fun isValidCase() = directions.containsAll(listOf(
            RelativeRotationDirection.Front,
            RelativeRotationDirection.Back,
            RelativeRotationDirection.Left,
            RelativeRotationDirection.Right))

        if(!canFullCrossingForm()){
            return false
        }

        if(isValidCase()){
            updateInstance(WIRE_CROSSING_FULL, Quaternion.ONE)
            return true
        }

        return false
    }

    private fun updateInstance(model : PartialModel, rotation : Quaternion){
        modelInstance?.delete()

        // Conversion equation:
        // let S - world size, B - block bench size
        // S = B / 16
        val size = 1.5 / 16

        // todo: it still looks a little bit off the ground, why?

        modelInstance = multipartInstance.materialManager
            .defaultSolid()
            .material(Materials.TRANSFORMED)
            .getModel(model)
            .createInstance()
            .loadIdentity()
            .translate(part.placementContext.face.opposite.normal.toVec3() * Vec3(size, size, size))
            .blockCenter()
            .translate(part.worldBoundingBox.center)
            .multiply(part.placementContext.face.rotation * part.facingRotation * rotation)
            .zeroCenter()

        multipartInstance.relightPart(part)
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
