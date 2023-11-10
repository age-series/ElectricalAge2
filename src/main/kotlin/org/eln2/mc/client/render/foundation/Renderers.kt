package org.eln2.mc.client.render.foundation

import com.jozufozu.flywheel.api.MaterialManager
import com.jozufozu.flywheel.api.instance.DynamicInstance
import com.jozufozu.flywheel.backend.instancing.blockentity.BlockEntityInstance
import com.jozufozu.flywheel.core.Materials
import com.jozufozu.flywheel.core.PartialModel
import com.jozufozu.flywheel.core.materials.FlatLit
import com.jozufozu.flywheel.core.materials.model.ModelData
import com.jozufozu.flywheel.util.Color
import com.jozufozu.flywheel.util.transform.Transform
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.LightLayer
import org.ageseries.libage.sim.thermal.STANDARD_TEMPERATURE
import org.ageseries.libage.sim.thermal.Temperature
import org.ageseries.libage.sim.thermal.ThermalUnits
import org.eln2.mc.*
import org.eln2.mc.common.blocks.foundation.MultipartBlockEntity
import org.eln2.mc.common.content.PartConnectionRenderInfo
import org.eln2.mc.common.content.PartConnectionRenderInfoSetConsumer
import org.eln2.mc.common.content.WirePolarPatchModel
import org.eln2.mc.common.content.getPartConnectionAsContactSectionConnectionOrNull
import org.eln2.mc.common.events.AtomicUpdate
import org.eln2.mc.common.parts.foundation.*
import org.eln2.mc.mathematics.Base6Direction3d
import org.eln2.mc.mathematics.Base6Direction3dMask
import org.eln2.mc.mathematics.Vector3d
import org.eln2.mc.mathematics.map

fun createPartInstance(
    multipart: MultipartBlockEntityInstance,
    model: PartialModel,
    part: Part<*>,
    yRotation: Double = 0.0,
): ModelData {
    return multipart.materialManager
        .defaultSolid()
        .material(Materials.TRANSFORMED)
        .getModel(model)
        .createInstance()
        .loadIdentity()
        .transformPart(part, yRotation)
}

/**
 * Part renderer with a single model.
 * */
open class BasicPartRenderer(val part: Part<*>, val model: PartialModel) : PartRenderer() {
    var yRotation = 0.0

    private var modelInstance: ModelData? = null

    override fun setupRendering() {
        buildInstance()
    }

    fun buildInstance() {
        modelInstance?.delete()
        modelInstance = createPartInstance(multipart, model, part, yRotation)
    }

    override fun relight(source: RelightSource) {
        multipart.relightModels(modelInstance)
    }

    override fun beginFrame() {}

    override fun remove() {
        modelInstance?.delete()
    }
}

fun CellPart<*, ConnectedPartRenderer>.getConnectedPartTag() = CompoundTag().also { compoundTag ->
    if(this.hasCell) {
        val values = IntArrayList(2)

        for (it in this.cell.connections) {
            val solution = getPartConnectionAsContactSectionConnectionOrNull(this.cell, it)
                ?: continue

            values.add(solution.value)
        }

        compoundTag.putIntArray("connections", values)
    }
}

fun Part<ConnectedPartRenderer>.handleConnectedPartTag(tag: CompoundTag) = this.renderer.acceptConnections(
    if(tag.contains("connections")) {
        tag.getIntArray("connections")
    }
    else {
        IntArray(0)
    }
)

data class WireConnectionModelPartial(
    val planar: PolarModel,
    val inner: PolarModel,
    val wrapped: PolarModel
) {
    val variants = mapOf(
        CellPartConnectionMode.Planar to planar,
        CellPartConnectionMode.Inner to inner,
        CellPartConnectionMode.Wrapped to wrapped
    )
}

class ConnectedPartRenderer(
    val part: Part<*>,
    val body: PartialModel,
    val connections: Map<Base6Direction3d, WireConnectionModelPartial>
) : PartRenderer(), PartConnectionRenderInfoSetConsumer {
    constructor(part: Part<*>, body: PartialModel, connection: WireConnectionModelPartial) : this(
        part,
        body,
        mapOf(
            Base6Direction3d.Front to connection,
            Base6Direction3d.Back to connection,
            Base6Direction3d.Left to connection,
            Base6Direction3d.Right to connection
        )
    )

    private var bodyInstance: ModelData? = null
    private val connectionDirectionsUpdate = AtomicUpdate<IntArray>()
    private val connectionInstances = Int2ObjectOpenHashMap<ModelData>()

    override fun acceptConnections(connections: IntArray) {
        connectionDirectionsUpdate.setLatest(connections)
    }

    override fun setupRendering() {
        buildBody()
        applyConnectionData(connectionInstances.keys.toIntArray())
    }

    private fun buildBody() {
        bodyInstance?.delete()
        bodyInstance = createPartInstance(multipart, body, part)
    }

    private fun applyConnectionData(values: IntArray) {
        connectionInstances.values.forEach { it.delete() }
        connectionInstances.clear()

        for (value in values) {
            val info = PartConnectionRenderInfo(value)
            val direction = info.directionPart

            val model = connections[direction]
                ?: continue

            val instance = createPartInstance(
                multipart,
                model.variants[info.mode]!!,
                part,
                when (direction) {
                    Base6Direction3d.Front -> 0.0
                    Base6Direction3d.Back -> kotlin.math.PI
                    Base6Direction3d.Left -> kotlin.math.PI / 2.0
                    Base6Direction3d.Right -> -kotlin.math.PI / 2.0
                    else -> error("Invalid connected part direction $direction")
                }
            )

            connectionInstances.putUnique(value, instance)
        }

        multipart.relightModels(connectionInstances.values)
    }

    override fun relight(source: RelightSource) {
        multipart.relightModels(bodyInstance)
        multipart.relightModels(connectionInstances.values)
    }

    override fun beginFrame() {
        connectionDirectionsUpdate.consume { values ->
            applyConnectionData(values)
        }
    }

    override fun remove() {
        bodyInstance?.delete()
        connectionInstances.values.forEach { it.delete() }
    }
}

private val offsetTable = Int2ObjectOpenHashMap<Vector3d>(6).also {
    it[Direction.DOWN.index()] = Vector3d(0.5, 1.0, 0.5)
    it[Direction.UP.index()] = Vector3d(0.5, 0.0, 0.5)
    it[Direction.NORTH.index()] = Vector3d(0.5, 0.5, 1.0)
    it[Direction.SOUTH.index()] = Vector3d(0.5, 0.5, 0.0)
    it[Direction.WEST.index()] = Vector3d(1.0, 0.5, 0.5)
    it[Direction.EAST.index()] = Vector3d(0.0, 0.5, 0.5)
}

fun<T : Transform<T>> T.transformPart(part: Part<*>, yRotation: Double = 0.0): T {
    val (dx, dy, dz) = offsetTable.get(part.placement.face.index())!!

    return this
        .translate(part.placement.position)
        .translate(dx, dy, dz)
        .multiply(part.placement.face.rotation)
        .rotateYRadians(PartGeometry.facingRotationLog(part.placement.horizontalFacing))
        .rotateYRadians(yRotation)
        .translate(-0.5, 0.0, -0.5)
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
