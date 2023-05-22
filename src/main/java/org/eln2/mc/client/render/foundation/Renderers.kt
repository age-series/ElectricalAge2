package org.eln2.mc.client.render.foundation

import com.jozufozu.flywheel.api.MaterialManager
import com.jozufozu.flywheel.api.instance.DynamicInstance
import com.jozufozu.flywheel.backend.instancing.blockentity.BlockEntityInstance
import com.jozufozu.flywheel.core.Materials
import com.jozufozu.flywheel.core.PartialModel
import com.jozufozu.flywheel.core.materials.FlatLit
import com.jozufozu.flywheel.core.materials.model.ModelData
import com.jozufozu.flywheel.util.Color
import com.mojang.math.Vector3f
import org.ageseries.libage.sim.thermal.STANDARD_TEMPERATURE
import org.ageseries.libage.sim.thermal.Temperature
import org.ageseries.libage.sim.thermal.ThermalUnits
import org.eln2.mc.ClientOnly
import org.eln2.mc.common.blocks.foundation.MultipartBlockEntity
import org.eln2.mc.common.parts.foundation.PartRenderer
import org.eln2.mc.common.parts.foundation.Part
import org.eln2.mc.common.parts.foundation.PartUpdateType
import org.eln2.mc.extensions.blockCenter
import org.eln2.mc.extensions.times
import org.eln2.mc.extensions.toVec3
import org.eln2.mc.extensions.zeroCenter
import org.eln2.mc.mathematics.map
import org.eln2.mc.mathematics.vec3

fun createPartInstance(multipart: MultipartBlockEntityInstance, model: PartialModel, part: Part, downOffset: Double, yRotation: Float): ModelData {
    return multipart.materialManager
        .defaultSolid()
        .material(Materials.TRANSFORMED)
        .getModel(model)
        .createInstance()
        .loadIdentity()
        .applyBlockBenchTransform(part, downOffset, yRotation)
}

/**
 * The basic part renderer is used to render a single partial model.
 * */
open class BasicPartRenderer(val part: Part, val model: PartialModel) : PartRenderer {
    override fun isSetupWith(multipartBlockEntityInstance: MultipartBlockEntityInstance): Boolean {
        return this::multipart.isInitialized && this.multipart == multipartBlockEntityInstance
    }

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

        modelInstance = createPartInstance(multipart, model, part, downOffset, yRotation)

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

fun ModelData.applyBlockBenchTransform(part: Part, downOffset: Double, yRotation: Float = 0f): ModelData {
    return this
        .translate(part.placement.face.opposite.normal.toVec3() * vec3(downOffset))
        .blockCenter()
        .translate(part.worldBoundingBox.center)
        .multiply(
            part.placement.face.rotation *
                part.txFacing *
                Vector3f.YP.rotationDegrees(yRotation))
        .zeroCenter()
}

class RadiantBodyColorBuilder {
    var coldTint = colorF(1f, 1f, 1f, 1f)
    var hotTint = colorF(5f, 0.1f, 0.2f, 1f)
    var coldTemperature = STANDARD_TEMPERATURE
    var hotTemperature = Temperature.from(1000.0, ThermalUnits.CELSIUS)

    fun build(): RadiantBodyColor {
        return RadiantBodyColor(
            coldTint,
            hotTint,
            coldTemperature,
            hotTemperature,
        )
    }
}

fun defaultRadiantBodyColor(): RadiantBodyColor {
    return RadiantBodyColorBuilder().build()
}

class RadiantBodyColor(
    val coldTint: Color,
    val hotTint: Color,
    val coldTemperature: Temperature,
    val hotTemperature : Temperature,
) {
    fun evaluate(t: Temperature): Color {
        val progress = map(
            t.kelvin.coerceIn(coldTemperature.kelvin, hotTemperature.kelvin),
            coldTemperature.kelvin,
            hotTemperature.kelvin,
            0.0,
            1.0
        )

        return colorLerp(coldTint, hotTint, progress.toFloat())
    }

    fun evaluate(t: Double): Color {
        return evaluate(Temperature(t))
    }
}

@ClientOnly
class MultipartBlockEntityInstance(val materialManager: MaterialManager, blockEntity: MultipartBlockEntity) :
    BlockEntityInstance<MultipartBlockEntity>(materialManager, blockEntity),
    DynamicInstance {

    private val parts = ArrayList<Part>()

    override fun init() {
        super.init()

        blockEntity.bindRenderer(this)
    }

    /**
     * Called by flywheel at the start of each frame.
     * This applies any part updates (new or removed parts), and notifies the part renderers about the new frame.
     * */
    override fun beginFrame() {
        handlePartUpdates()

        parts.forEach { part ->
            val renderer = part.renderer

            // todo: maybe get jozufozu to document this?
            if(!renderer.isSetupWith(this)){
                renderer.setupRendering(this)
            }

            renderer.beginFrame()
        }
    }

    /**
     * Called by flywheel when a re-light is required.
     * This applies a re-light to all the part renderers.
     * */
    override fun updateLight() {
        parts.forEach { part ->
            relightPart(part)
        }
    }

    /**
     * This method is called at the start of each frame.
     * It dequeues all the part updates that were handled on the game thread.
     * These updates may indicate:
     *  - New parts added to the multipart.
     *  - Parts that were destroyed.
     * */
    private fun handlePartUpdates() {
        while (true) {
            val update = blockEntity.renderQueue.poll() ?: break
            val part = update.part

            when (update.type) {
                PartUpdateType.Add -> {
                    // Parts may already be added, because of the bind method that we called.

                    if (!parts.contains(part)) {
                        parts.add(part)
                        part.renderer.setupRendering(this)
                        relightPart(part)
                    }
                }

                PartUpdateType.Remove -> {
                    parts.remove(part)
                    part.destroyRenderer()
                }
            }
        }
    }

    /**
     * Called by flywheel when this renderer is no longer needed.
     * This also calls a cleanup method on the part renderers.
     * */
    override fun remove() {
        parts.forEach { part ->
            part.destroyRenderer()
        }

        blockEntity.unbindRenderer()
    }

    /**
     * This is called by parts when they need to force a re-light.
     * This may happen when a model is initially created.
     * */
    fun relightPart(part: Part) {
        val models = part.renderer.relightModels()

        if (models != null) {
            relight(pos, models.stream())
        }
    }
}
