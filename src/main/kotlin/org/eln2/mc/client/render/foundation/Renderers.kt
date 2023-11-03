package org.eln2.mc.client.render.foundation

import com.jozufozu.flywheel.api.MaterialManager
import com.jozufozu.flywheel.api.instance.DynamicInstance
import com.jozufozu.flywheel.backend.instancing.blockentity.BlockEntityInstance
import com.jozufozu.flywheel.core.Materials
import com.jozufozu.flywheel.core.PartialModel
import com.jozufozu.flywheel.core.materials.FlatLit
import com.jozufozu.flywheel.core.materials.model.ModelData
import com.jozufozu.flywheel.util.Color
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.LightLayer
import org.ageseries.libage.sim.thermal.STANDARD_TEMPERATURE
import org.ageseries.libage.sim.thermal.Temperature
import org.ageseries.libage.sim.thermal.ThermalUnits
import org.eln2.mc.*
import org.eln2.mc.common.blocks.foundation.MultipartBlockEntity
import org.eln2.mc.common.events.AtomicUpdate
import org.eln2.mc.common.parts.foundation.*
import org.eln2.mc.mathematics.Base6Direction3d
import org.eln2.mc.mathematics.Base6Direction3dMask
import org.eln2.mc.mathematics.map
import org.joml.AxisAngle4f
import org.joml.Quaternionf
import org.joml.Vector3f

fun createPartInstance(
    multipart: MultipartBlockEntityInstance,
    model: PartialModel,
    part: Part<*>,
    downOffset: Double,
    yRotation: Double = 0.0,
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
 * Part renderer with a single model.
 * */
open class BasicPartRenderer(val part: Part<*>, val model: PartialModel) : PartRenderer() {
    var yRotation = 0.0
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

fun interface ConnectionMaskConsumer {
    /**
     * Accepts the connection directions encoded as [mask].
     * The connection directions are all in the local frame.
     * */
    fun acceptDirections(mask: Base6Direction3dMask)
}

fun CellPart<*, ConnectedPartRenderer>.getConnectedPartTag() = CompoundTag().also { compoundTag ->
    if(this.hasCell) {
        var mask = Base6Direction3dMask.EMPTY

        for (it in this.cell.connections) {
            val solution = getPartConnectionOrNull(this.cell.locator, it.locator)
                ?: continue

            mask += solution.directionPart
        }

        compoundTag.putInt("mask", mask.value)
    }
}

fun Part<ConnectedPartRenderer>.handleConnectedPartTag(tag: CompoundTag) = this.renderer.acceptDirections(
    if(tag.contains("mask")) {
        Base6Direction3dMask(
            tag.getInt("mask")
        )
    }
    else {
        Base6Direction3dMask.EMPTY
    }
)

class ConnectedPartRenderer(
    val part: Part<*>,
    val body: PartialModel,
    val connections: Map<Base6Direction3d, Pair<PartialModel, Double>>
) : PartRenderer(), ConnectionMaskConsumer {
    constructor(part: Part<*>, body: PartialModel, connection: PartialModel, connectionDown: Double = 0.0) : this(
        part,
        body,
        Pair(connection, connectionDown).let {
            mapOf(
                Base6Direction3d.Front to it,
                Base6Direction3d.Back to it,
                Base6Direction3d.Left to it,
                Base6Direction3d.Right to it
            )
        }
    )

    var bodyDownOffset = 0.0

    private var bodyInstance: ModelData? = null
    private var connectionDirections = Base6Direction3dMask.EMPTY
    private val connectionDirectionsUpdate = AtomicUpdate<Base6Direction3dMask>()
    private val connectionInstances = Int2ObjectOpenHashMap<ModelData>()

    override fun acceptDirections(mask: Base6Direction3dMask) {
        connectionDirectionsUpdate.setLatest(mask)
    }

    override fun setupRendering() {
        buildBody()
        buildConnections()
    }

    private fun buildBody() {
        bodyInstance?.delete()
        bodyInstance = createPartInstance(multipart, body, part, bodyDownOffset)
    }

    private fun buildConnections() {
        connectionInstances.values.forEach { it.delete() }
        connectionInstances.clear()

        for (direction in connectionDirections.directionList) {
            val modelInfo = connections[direction.alias]
                ?: continue

            val instance = multipart.materialManager
                .defaultSolid()
                .material(Materials.TRANSFORMED)
                .getModel(modelInfo.first)
                .createInstance()
                .loadIdentity()
                .translateNormal(part.placement.face.opposite, modelInfo.second)
                .translate(0.5, 0.0, 0.5)
                .translate(part.worldBoundingBox.center)
                .multiply(
                    part.placement.face.rotation.toJoml()
                        .mul(part.facingRotation)
                        .rotateY(
                            when (direction.alias) {
                                Base6Direction3d.Front -> 0.0
                                Base6Direction3d.Back -> kotlin.math.PI
                                Base6Direction3d.Left -> kotlin.math.PI / 2.0
                                Base6Direction3d.Right -> -kotlin.math.PI / 2.0
                                else -> error("Invalid connected part direction $direction")
                            }.toFloat()
                        )
                        .toMinecraft()
                )
                .translate(-0.5, 0.0, -0.5)

            connectionInstances.putUnique(direction.index(), instance)
        }
    }

    override fun relight(source: RelightSource) {
        multipart.relightModels(bodyInstance)
        multipart.relightModels(connectionInstances.values)
    }

    override fun beginFrame() {
        connectionDirectionsUpdate.consume { mask ->
            this.connectionDirections = mask
            buildConnections()
            multipart.relightModels(connectionInstances.values)
        }
    }

    override fun remove() {
        bodyInstance?.delete()
        connectionInstances.values.forEach { it.delete() }
    }
}
// down offset -> world units
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
