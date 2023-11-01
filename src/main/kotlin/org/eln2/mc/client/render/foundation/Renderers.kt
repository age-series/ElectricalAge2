package org.eln2.mc.client.render.foundation

import com.jozufozu.flywheel.api.MaterialManager
import com.jozufozu.flywheel.api.instance.DynamicInstance
import com.jozufozu.flywheel.backend.instancing.blockentity.BlockEntityInstance
import com.jozufozu.flywheel.core.Materials
import com.jozufozu.flywheel.core.PartialModel
import com.jozufozu.flywheel.core.materials.FlatLit
import com.jozufozu.flywheel.core.materials.model.ModelData
import com.jozufozu.flywheel.util.Color
import net.minecraft.world.level.LightLayer
import org.ageseries.libage.sim.thermal.STANDARD_TEMPERATURE
import org.ageseries.libage.sim.thermal.Temperature
import org.ageseries.libage.sim.thermal.ThermalUnits
import org.eln2.mc.*
import org.eln2.mc.common.blocks.foundation.MultipartBlockEntity
import org.eln2.mc.common.parts.foundation.Part
import org.eln2.mc.common.parts.foundation.PartRenderer
import org.eln2.mc.common.parts.foundation.PartUpdateType
import org.eln2.mc.common.parts.foundation.RelightSource
import org.eln2.mc.mathematics.map
import org.joml.AxisAngle4f
import org.joml.Quaternionf
import org.joml.Vector3f

fun createPartInstance(
    multipart: MultipartBlockEntityInstance,
    model: PartialModel,
    part: Part<*>,
    downOffset: Double,
    yRotation: Double,
): ModelData {
    return multipart.materialManager
        .defaultSolid()
        .material(Materials.TRANSFORMED)
        .getModel(model)
        .createInstance()
        .loadIdentity()
        .applyBlockBenchTransform(part, downOffset, yRotation.toFloat())
}

/**
 * The basic part renderer is used to render a single partial model.
 * */
open class BasicPartRenderer(val part: Part<*>, val model: PartialModel) : PartRenderer() {
    /**
     * Useful if the model needs to be rotated to match the networked behavior.
     * Alternatively, the model may be rotated in the 3D editor.
     * */
    var yRotation = 0.0

    /**
     * Required in order to "place" the model on the mounting surface. Usually, this offset is calculated using information from the 3D editor.
     * */
    var downOffset = 0.0

    private var modelInstance: ModelData? = null

    override fun setupRendering() {
        buildInstance()
    }

    fun buildInstance() {
        modelInstance?.delete()
        modelInstance = createPartInstance(multipart, model, part, downOffset, yRotation)
    }

    override fun relight(source: RelightSource) {
        multipart.relightModels(modelInstance)
    }

    override fun beginFrame() {}

    override fun remove() {
        modelInstance?.delete()
    }
}

fun ModelData.applyBlockBenchTransform(part: Part<*>, downOffset: Double, yRotation: Float = 0f): ModelData {
    return this
        .translate(part.placement.face.opposite.normal.toVec3() * downOffset)
        .blockCenter()
        .translate(part.worldBoundingBox.center)
        .multiply(
            part.placement.face.rotation.toJoml()
                .mul(part.facingRotation)
                .mul(
                    Quaternionf(
                        AxisAngle4f(
                            yRotation,
                            Vector3f(0.0f, 1.0f, 0.0f)
                        )
                    )
                ).toMinecraft()
        )
        .zeroCenter()
}

class RadiantBodyColorBuilder {
    var coldTint = colorF(1f, 1f, 1f, 1f)
    var hotTint = colorF(1f, 0.2f, 0.1f, 1f)
    var coldTemperature = STANDARD_TEMPERATURE
    var hotTemperature = Temperature.from(800.0, ThermalUnits.CELSIUS)

    fun build(): ThermalTint {
        return ThermalTint(
            coldTint,
            hotTint,
            coldTemperature,
            hotTemperature,
        )
    }
}

fun defaultRadiantBodyColor(): ThermalTint {
    return RadiantBodyColorBuilder().build()
}

class ThermalTint(
    val coldTint: Color,
    val hotTint: Color,
    val coldTemperature: Temperature,
    val hotTemperature: Temperature,
) {
    fun evaluate(temperature: Temperature) =
        colorLerp(
            from = coldTint,
            to = hotTint,
            blend = map(
                temperature.kelvin.coerceIn(
                    coldTemperature.kelvin,
                    hotTemperature.kelvin
                ),
                coldTemperature.kelvin,
                hotTemperature.kelvin,
                0.0,
                1.0
            ).toFloat()
        )

    fun evaluate(t: Double) = evaluate(Temperature(t))
}

@ClientOnly
class MultipartBlockEntityInstance(
    val materialManager: MaterialManager,
    blockEntity: MultipartBlockEntity,
) : BlockEntityInstance<MultipartBlockEntity>(materialManager, blockEntity), DynamicInstance {

    private val parts = HashSet<Part<*>>()

    override fun init() {
        // When this is called on an already initialized renderer (e.g. changing graphics settings),
        // we will get the parts in handlePartUpdates
        blockEntity.bindRenderer(this)
    }

    fun readBlockBrightness() = world.getBrightness(LightLayer.BLOCK, pos)

    fun readSkyBrightness() = world.getBrightness(LightLayer.SKY, pos)

    /**
     * Called by flywheel at the start of each frame.
     * This applies any part updates (new or removed parts), and notifies the part renderers about the new frame.
     * */
    override fun beginFrame() {
        handlePartUpdates()

        for (part in parts) {
            val renderer = part.renderer

            if (!renderer.isSetupWith(this)) {
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
        for (part in parts) {
            part.renderer.relight(RelightSource.BlockEvent)
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
            val update = blockEntity.renderUpdates.poll() ?: break
            val part = update.part

            when (update.type) {
                PartUpdateType.Add -> {
                    parts.add(part)
                    part.renderer.setupRendering(this)
                    part.renderer.relight(RelightSource.Setup)
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
        for (part in parts) {
            part.destroyRenderer()
        }

        blockEntity.unbindRenderer()
    }

    // Nullable for convenience

    /**
     * Relights the [models] using the block and skylight at this position.
     * */
    fun relightModels(models: Iterable<FlatLit<*>?>) {
        val block = readBlockBrightness()
        val sky = readSkyBrightness()

        for (it in models) {
            if(it != null) {
                it.setBlockLight(block)
                it.setSkyLight(sky)
            }
        }
    }

    /**
     * Relights the [models] using the block and skylight at this position.
     * */
    fun relightModels(vararg models: FlatLit<*>?) = relightModels(models.asIterable())
}

fun interface PartRendererSupplier<T : Part<R>, R : PartRenderer> {
    fun create(part: T) : R
}
