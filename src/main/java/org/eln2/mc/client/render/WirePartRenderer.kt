package org.eln2.mc.client.render
/*

import com.jozufozu.flywheel.core.Materials
import com.jozufozu.flywheel.core.PartialModel
import com.jozufozu.flywheel.core.materials.FlatLit
import com.jozufozu.flywheel.core.materials.model.ModelData
import com.mojang.math.Quaternion
import com.mojang.math.Vector3f
import net.minecraft.world.phys.Vec3
import org.eln2.mc.Eln2
import org.eln2.mc.client.render.PartialModels.WIRE_CORNER
import org.eln2.mc.client.render.PartialModels.WIRE_CROSSING
import org.eln2.mc.client.render.PartialModels.WIRE_CROSSING_EMPTY
import org.eln2.mc.client.render.PartialModels.WIRE_CROSSING_FULL
import org.eln2.mc.client.render.PartialModels.WIRE_CROSSING_SINGLE_WIRE
import org.eln2.mc.client.render.PartialModels.WIRE_STRAIGHT
import org.eln2.mc.common.parts.WirePart
import org.eln2.mc.common.parts.foundation.IPartRenderer
import org.eln2.mc.common.space.DirectionMask
import org.eln2.mc.common.space.RelativeRotationDirection
import org.eln2.mc.extensions.DirectionMaskExtensions.matchCounterClockWise
import org.eln2.mc.extensions.ModelDataExtensions.blockCenter
import org.eln2.mc.extensions.ModelDataExtensions.zeroCenter
import org.eln2.mc.extensions.QuaternionExtensions.times
import org.eln2.mc.extensions.Vec3Extensions.times
import org.eln2.mc.extensions.Vec3Extensions.toVec3
import java.util.concurrent.atomic.AtomicReference

class WirePartRenderer(val part: WirePart) : IPartRenderer {
    companion object {
        // P.S. these are also written in respect to the model rotation.

        private val caseMap = mapOf(
            (DirectionMask.EMPTY) to WIRE_CROSSING_EMPTY,
            (DirectionMask.FRONT) to WIRE_CROSSING_SINGLE_WIRE,
            (DirectionMask.FRONT + DirectionMask.BACK) to WIRE_STRAIGHT,
            (DirectionMask.FRONT + DirectionMask.LEFT) to WIRE_CORNER,
            (DirectionMask.LEFT + DirectionMask.FRONT + DirectionMask.RIGHT) to WIRE_CROSSING,
            (DirectionMask.HORIZONTALS) to WIRE_CROSSING_FULL
        )
    }

    private lateinit var multipartInstance: MultipartBlockEntityInstance
    private var modelInstance: ModelData? = null

    // Reset on every frame
    private var latestDirections = AtomicReference<List<RelativeRotationDirection>>()

    fun applyDirections(directions: List<RelativeRotationDirection>) {
        latestDirections.set(directions)
    }

    override fun setupRendering(multipart: MultipartBlockEntityInstance) {
        multipartInstance = multipart
    }

    override fun beginFrame() {
        selectModel()
    }

    private fun selectModel() {
        val directions =
            latestDirections.getAndSet(null)
                ?: return

        val mask = DirectionMask.ofRelatives(directions)

        var found = false

        // Here, we search for the correct configuration by just rotating all the cases we know.
        caseMap.forEach { (mappingMask, model) ->
            val match = mappingMask.matchCounterClockWise(mask)

            if (match != -1) {
                found = true

                updateInstance(model, Vector3f.YP.rotationDegrees(90f * match))

                return@forEach
            }
        }

        if (!found) {
            Eln2.LOGGER.error("Wire did not handle cases: $directions")
        }
    }

    private fun updateInstance(model: PartialModel, rotation: Quaternion) {
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
        if (modelInstance != null) {
            return listOf(modelInstance!!)
        }

        return listOf()
    }

    override fun remove() {
        modelInstance?.delete()
    }
}
*/
