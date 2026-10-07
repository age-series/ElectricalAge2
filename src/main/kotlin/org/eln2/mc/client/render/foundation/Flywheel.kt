package org.eln2.mc.client.render.foundation

import dev.engine_room.flywheel.api.instance.Instance
import dev.engine_room.flywheel.api.instance.InstanceHandle
import dev.engine_room.flywheel.api.instance.InstanceType
import dev.engine_room.flywheel.api.layout.FloatRepr
import dev.engine_room.flywheel.api.layout.IntegerRepr
import dev.engine_room.flywheel.api.layout.LayoutBuilder
import dev.engine_room.flywheel.api.material.Material
import dev.engine_room.flywheel.api.model.Model
import dev.engine_room.flywheel.api.visual.*
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry
import dev.engine_room.flywheel.lib.instance.InstanceTypes
import dev.engine_room.flywheel.lib.instance.SimpleInstanceType
import dev.engine_room.flywheel.lib.instance.TransformedInstance
import dev.engine_room.flywheel.lib.material.SimpleMaterial
import dev.engine_room.flywheel.lib.model.Models
import dev.engine_room.flywheel.lib.model.baked.BakedModelBuilder
import dev.engine_room.flywheel.lib.model.baked.PartialModel
import dev.engine_room.flywheel.lib.task.PlanMap
import dev.engine_room.flywheel.lib.transform.Affine
import dev.engine_room.flywheel.lib.util.ExtraMemoryOps
import dev.engine_room.flywheel.lib.util.RendererReloadCache
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import org.ageseries.libage.data.OptionalDouble
import org.ageseries.libage.data.Quantity
import org.ageseries.libage.data.Temperature
import org.ageseries.libage.mathematics.geometry.*
import org.ageseries.libage.utils.putUnique
import org.eln2.mc.*
import org.eln2.mc.client.render.FlwMaterials
import org.eln2.mc.client.render.FlwModels
import org.eln2.mc.client.render.foundation.PartWithKnobsVisual.Companion.createKnobList
import org.eln2.mc.client.render.foundation.PartWithKnobsVisual.Companion.transformKnobList
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.blocks.foundation.BigBlockRepresentativeBlockEntity
import org.eln2.mc.common.blocks.foundation.MultipartBlockEntityVisual
import org.eln2.mc.common.blocks.foundation.MultipartVisualizationContext
import org.eln2.mc.common.cells.foundation.KineticInterpolatorClient
import org.eln2.mc.common.cells.foundation.RotatingKineticState
import org.eln2.mc.common.content.PartConnectionRenderInfo
import org.eln2.mc.common.content.WireConnectionModelPartial
import org.eln2.mc.common.content.WirePart
import org.eln2.mc.common.content.getPartConnectionAsContactSectionConnectionOrNull
import org.eln2.mc.common.grids.GridConnectionCell
import org.eln2.mc.common.parts.foundation.*
import org.eln2.mc.common.specs.foundation.*
import org.eln2.mc.extensions.*
import org.eln2.mc.mathematics.Axis3d
import org.eln2.mc.mathematics.Base6Direction3d
import org.eln2.mc.mathematics.maskXY
import org.joml.Quaternionf
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.util.function.Consumer
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.math.PI

fun interface PartVisualizerSupplier<P : Part> {
    fun get() : PartVisualizer<P>
}

object FlwVisualizerRegistry {
    private val partVisualizerRegistry = HashMap<PartProvider, PartVisualizer<*>>()
    private val specVisualizerRegistry = HashMap<SpecProvider, SpecVisualizer<*>>()

    fun <P : Part> setPartVisualizer(partProvider: PartProvider, visualizer: PartVisualizer<P>) {
        if(partVisualizerRegistry.contains(partProvider)) {
            error("Duplicate register part visualizer ${partProvider.id}")
        }

        partVisualizerRegistry[partProvider] = visualizer
    }

    fun <P : Part> setPartVisualizerMemoized(partProvider: PartProvider, memoizer: PartVisualizerSupplier<P>) =
        setPartVisualizer(partProvider, memoizer.get())

    fun <S : Spec> setSpecVisualizer(specProvider: SpecProvider, visualizer: SpecVisualizer<S>) {
        if(specVisualizerRegistry.contains(specProvider)) {
            error("Duplicate register spec visualizer ${specProvider.id}")
        }

        specVisualizerRegistry[specProvider] = visualizer
    }

    fun getPartVisualizer(provider: PartProvider) = partVisualizerRegistry[provider]
        ?: error(DEBUGGER_BREAK("Visualizer for part $provider not registered"))

    fun getSpecVisualizer(provider: SpecProvider) = specVisualizerRegistry[provider]
        ?: error(DEBUGGER_BREAK("Visualizer for spec $provider not registered"))

    fun registerFoundationalVisualizers() {
        VisualizerRegistry.setVisualizer(
            BlockRegistry.MULTIPART_BLOCK_ENTITY.get(),
            SimpleBlockEntityVisualizer(::MultipartBlockEntityVisual) { false /* ADDITIONAL RENDERER */ }
        )
    }
}

object FlwInstanceTypes {
    val TRANSFORMED_POLAR: SimpleInstanceType<TransformedPolarInstance> = SimpleInstanceType.builder(::TransformedPolarInstance)
        .cullShader(resource("instance/cull/default.glsl"))
        .vertexShader(resource("instance/transformed_polar.vert"))
        .layout(LayoutBuilder.create()
            .vector("color", FloatRepr.NORMALIZED_UNSIGNED_BYTE, 4)
            .vector("light", IntegerRepr.SHORT, 2)
            .vector("overlay", IntegerRepr.SHORT, 2)
            .vector("color1", FloatRepr.NORMALIZED_UNSIGNED_BYTE, 4)
            .vector("color2", FloatRepr.NORMALIZED_UNSIGNED_BYTE, 4)
            .matrix("pose", FloatRepr.FLOAT, 4)
            .build()
        )
        .writer { ptr, instance ->
            MemoryUtil.memPutByte(ptr + 0, instance.red)
            MemoryUtil.memPutByte(ptr + 1, instance.green)
            MemoryUtil.memPutByte(ptr + 2, instance.blue)
            MemoryUtil.memPutByte(ptr + 3, instance.alpha)
            ExtraMemoryOps.put2x16(ptr + 4, instance.light)
            ExtraMemoryOps.put2x16(ptr + 8, instance.overlay)
            instance.color1.blit(ptr + 12)
            instance.color2.blit(ptr + 16)
            ExtraMemoryOps.putMatrix4f(ptr + 20, instance.pose)
        }
        .build()

    val TRANSFORMED_LIGHT_OVERRIDE: InstanceType<TransformedLightOverrideInstance> = SimpleInstanceType.builder(::TransformedLightOverrideInstance)
        .cullShader(resource("instance/cull/default.glsl"))
        .vertexShader(resource("instance/transformed_light_override.vert"))
        .layout(
            LayoutBuilder.create()
                .vector("color", FloatRepr.NORMALIZED_UNSIGNED_BYTE, 4)
                .vector("overlay", IntegerRepr.SHORT, 2)
                .vector("light", FloatRepr.UNSIGNED_SHORT, 2)
                .matrix("pose", FloatRepr.FLOAT, 4)
                .scalar("lightOverride", FloatRepr.FLOAT)
                .build()
        )
        .writer { ptr: Long, instance: TransformedLightOverrideInstance ->
            MemoryUtil.memPutByte(ptr + 0, instance.red)
            MemoryUtil.memPutByte(ptr + 1, instance.green)
            MemoryUtil.memPutByte(ptr + 2, instance.blue)
            MemoryUtil.memPutByte(ptr + 3, instance.alpha)
            ExtraMemoryOps.put2x16(ptr + 4, instance.overlay)
            ExtraMemoryOps.put2x16(ptr + 8, instance.light)
            ExtraMemoryOps.putMatrix4f(ptr + 12, instance.pose)
            MemoryUtil.memPutFloat(ptr + 76, instance.lightOverride)
        }
        .build()

    fun init() {
        LOG.info("Created ELN2 Flw instance types.")
    }
}

class TransformedPolarInstance(
    type: InstanceType<TransformedPolarInstance>,
    handle: InstanceHandle,
) : TransformedInstance(type, handle) {
    var color1 = MyColor(0)
    var color2 = MyColor(0)
}

class TransformedLightOverrideInstance(
    type: InstanceType<TransformedLightOverrideInstance>,
    handle: InstanceHandle
) : TransformedInstance(type, handle) {
    var lightOverride = 0.0f

    fun colorWithOverride(tint: ThermalTint, temperature: Quantity<Temperature>) : TransformedLightOverrideInstance {
        val color = tint.evaluate(temperature)
        color(color.r, color.g, color.b)
        lightOverride = color.a / 255.0f
        return this
    }
}

class SpecialVisualStorage<V : Visual> {
    val visuals = ArrayList<V>()
    val dynamicVisuals = PlanMap<DynamicVisual, DynamicVisual.Context>()
    val tickableVisuals = PlanMap<TickableVisual, TickableVisual.Context>()

    fun add(visual: V) {
        // Done once so no performance issues
        if(!visuals.add(visual)){
            error(DEBUGGER_BREAK("Duplicate add visual $visual"))
        }

        if(visual is DynamicVisual) {
            dynamicVisuals.add(visual, visual.planFrame())
        }

        if(visual is TickableVisual) {
            tickableVisuals.add(visual, visual.planTick())
        }
    }

    fun delete() {
        visuals.forEach { it.delete() }
        visuals.clear()
        dynamicVisuals.clear()
        tickableVisuals.clear()
    }

    fun remove(visual: Visual){
        visuals.remove(visual)

        if(visual is DynamicVisual) {
            dynamicVisuals.remove(visual)
        }

        if(visual is TickableVisual) {
            tickableVisuals.remove(visual)
        }
    }
}

object PartialModelHelper {
    private val PARTIAL_WITH_MATERIAL = RendererReloadCache<PartialWithMaterial, Model> { (partial, material) ->
        BakedModelBuilder.create(partial.get())
            .materialFunc { _, _ -> material }
            .build()
    }

    private data class PartialWithMaterial(val partialModel: PartialModel, val material: Material)

    fun applyMaterial(model: PartialModel, material: Material): Model = PARTIAL_WITH_MATERIAL.get(
        PartialWithMaterial(model, material)
    )
}

/**
 * Wraps a [PartialModel] and applies changes.
 * When the [get] is requested for the first time, [applyChanges] is called to modify a copy of the partial model.
 * */
abstract class ProcessedModel(modelLocation: ResourceLocation) {
    companion object {
        private val CACHE: RendererReloadCache<ProcessedModel, Model> =
            RendererReloadCache<ProcessedModel, Model> { it: ProcessedModel ->
                BakedModelBuilder.create(it.model ?: error("Partial model was null ${it.partialModel.modelLocation()}")).build()
            }

        private data class Key(val p: ProcessedModel, val material: Material)

        private val CACHE_WITH_MATERIAL: RendererReloadCache<Key, Model> =
            RendererReloadCache<Key, Model> { key: Key ->
                BakedModelBuilder
                    .create(key.p.model ?: error("Processed model was null ${key.p.partialModel.modelLocation()}"))
                    .materialFunc { _, _ -> key.material }
                    .build()
            }
    }

    val partialModel: PartialModel = PartialModel.of(modelLocation)

    private val obj = Any()
    private var lastInitialModel: BakedModel? = null
    private var processedModel: BakedModel? = null

    private val model: BakedModel? get() {
        synchronized(obj) {
            val partial = partialModel.get()

            if(partial == null) {
                lastInitialModel = null
                processedModel = null
                return null
            }

            if(lastInitialModel != partial) {
                lastInitialModel = partial
                processedModel = applyChanges(partial.bind())
            }

            return processedModel!!
        }
    }

    /**
     * Applies the changes to the [bakedModel]. The [bakedModel] is a copy of the model baked for the [partialModel], so it can be mutated freely.
     * @return The changed [bakedModel] or another model entirely.
     * */
    protected abstract fun applyChanges(bakedModel: BakedModel): BakedModel

    /**
     * Gets the model with the default material applied to it.
     * */
    fun get(): Model = CACHE.get(this)

    /**
     * Gets the model with the specified material applied to it.
     * */
    fun get(material: Material): Model = CACHE_WITH_MATERIAL.get(Key(this, material))
}

/**
 * Applies a post-processing step needed by the polar instance. The model must:
 * - Not have quads oriented towards north and south
 * - Be like a tube
 *
 * The vertex data is rotated so that, when it gets written to the vertex buffer, a special ordering of vertices is obtained:
 * Vertices 0, 1 are on one "pole" of the model (min z) and vertices 2, 3 are on the other (max z)
 * */
open class PolarModel(modelLocation: ResourceLocation) : ProcessedModel(modelLocation) {
    override fun applyChanges(bakedModel: BakedModel): BakedModel {
        val quadPositionAttributes = HashMap<BakedQuad, ArrayList<Vector3d>>()

        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        bakedModel.getQuads(null, null, null).forEach { quad ->
            if(quad.direction == Direction.NORTH || quad.direction == Direction.SOUTH) {
                error("Invalid connection model")
            }

            val positionList = ArrayList<Vector3d>()
            quadPositionAttributes[quad] = positionList

            require(quad.vertices.size == 32)

            val buffer = ByteBuffer.allocate(32)
            val intView = buffer.asIntBuffer()

            for (i in 0 until 4) {
                intView.clear()
                intView.put(quad.vertices, i * 8, 8)

                positionList.add(
                    Vector3d(
                        buffer.getFloat(0).toDouble(),
                        buffer.getFloat(4).toDouble(),
                        buffer.getFloat(8).toDouble(),
                    )
                )
            }
        }

        quadPositionAttributes.forEach { (quad, positions) ->
            require(positions.size == 4)

            data class VertexNode(
                val index: Int,
                val position: Vector3d,
                val next: Int,
            )

            val nodes = positions.mapIndexed { index, position ->
                val nextIndex = if(index == 3) {
                    0 // wrap around (circular linked list)
                }
                else {
                    index + 1
                }

                VertexNode(
                    index,
                    position,
                    nextIndex
                )
            }

            val headNode = nodes
                .sortedBy { it.position.z }
                .take(2)
                .let {
                    if(it[0].next == it[1].index) {
                        it[0]
                    }
                    else if(it[1].next == it[0].index) {
                        it[1]
                    }
                    else {
                        error("Disjoint head vertices")
                    }
                }

            val indices = headNode.let { head ->
                val nodeList = arrayListOf(head)

                repeat(3) {
                    nodeList.add(nodes[nodeList.last().next])
                }

                require(nodeList.distinct().size == nodeList.size && nodeList.size == 4)

                nodeList.map { it.index }
            }

            val vertexRecords = let {
                val buffer = ByteBuffer.allocate(8 * 4)
                val intView = buffer.asIntBuffer()
                val results = ArrayList<IntArray>(4)

                for (i in 0 until 4) {
                    intView.clear()
                    intView.put(quad.vertices, i * 8, 8)

                    val array = IntArray(8)

                    intView.rewind()
                    intView.get(array)
                    results.add(array)
                }

                results
            }

            val writer = IntBuffer.wrap(quad.vertices)
            writer.clear()

            indices.forEach { index ->
                writer.put(vertexRecords[index])
            }
        }

        return bakedModel
    }
}

/**
 * Represents a type of modification needed to a standard "tube" wire model that is placed in a specific part configuration.
 * The three possible configurations are:
 * - Straight - the wire connects with another wire in the same plane. The model is designed for this, so no changes are required.
 * - [Wrapped] - the wire connects to another wire on a perpendicular face of the same block, around the exterior. The patcher needs to extend the endpoint vertices so there is no gap at the corner.
 * - [Inner] - the wire connects to another wire on a perpendicular face of the same block, inside the wire. The patcher needs to retract the endpoint vertices so there is no overlap at the corner.
 * */
enum class WirePatchType {
    /**
     * Patches the chosen face to wrap around the corner of a block by translating vertices forward to create a sleeve
     * */
    Wrapped,
    /**
     * Patches the chosen face to pack in the corner of a block by translating vertices backward to create a slit
     * */
    Inner
}

class WirePatchPolarModel(modelLocation: ResourceLocation, val patchType: WirePatchType) : PolarModel(modelLocation) {
    override fun applyChanges(bakedModel: BakedModel): BakedModel {
        val polarModelSource = super.applyChanges(bakedModel)

        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        val quads = polarModelSource.getQuads(null, null, null).map {
            if(it.direction == Direction.NORTH || it.direction == Direction.SOUTH) {
                error("Invalid connection model")
            }

            it
        }.associateBy { it.direction }

        val headPositions = let {
            val results = HashMap<BakedQuad, List<Pair<Int, Vector3d>>>()

            quads.values.forEach { quad ->
                val positionList = ArrayList<Pair<Int, Vector3d>>(2)
                val buffer = ByteBuffer.allocate(32)
                val intView = buffer.asIntBuffer()

                for (i in 0 until 2) {
                    intView.clear()
                    intView.put(quad.vertices, i * 8, 8)

                    val vector = Vector3d(
                        buffer.getFloat(0).toDouble(),
                        buffer.getFloat(4).toDouble(),
                        buffer.getFloat(8).toDouble(),
                    )

                    positionList.add(i to vector)
                }

                positionList.sortBy { it.second.y }

                results[quad] = positionList
            }

            results
        }

        val size = headPositions[quads[Direction.EAST]!!]!!.let {
            it[1].second.y - it[0].second.y
        }

        require(size > 0.0)

        fun write(quad: BakedQuad, i: Int, value: Double) {
            val writer = IntBuffer.wrap(quad.vertices)
            writer.position(8 * i + 2)
            writer.put(value.toFloat().toBits())
        }

        val dz = when(patchType) {
            WirePatchType.Wrapped -> -size
            WirePatchType.Inner -> +size
        }

        quads[Direction.UP]!!.also { roof ->
            headPositions[roof]!!.forEach { p ->
                write(roof, p.first, p.second.z + dz)
            }
        }

        listOf(Direction.EAST, Direction.WEST).map { quads[it]!! }.forEach { wall ->
            val hVertex = headPositions[wall]!![1]
            write(wall, hVertex.first, hVertex.second.z + dz)
        }

        return polarModelSource
    }
}

/**
 * Holds variants of a wire connection model.
 * @param hubConnectionPlanar Planar variant of junction-hub connection
 * @param hubConnectionInner Inner variant of junction-hub connection
 * @param hubConnectionWrapped Wrapped variant of junction-hub connection
 * @param fullConnectionPlanar Planar variant of junction-center connection
 * @param fullConnectionInner Inner variant of junction-center connection
 * @param fullConnectionWrapped Wrapped variant of junction-center connection
 * */
class WireConnectionModel(
    hubConnectionPlanar: PolarModel,
    hubConnectionInner: PolarModel,
    hubConnectionWrapped: PolarModel,
    fullConnectionPlanar: PolarModel,
    fullConnectionInner: PolarModel,
    fullConnectionWrapped: PolarModel,
) {
    /**
     * Gets the Planar, Inner and Wrapped variants by fullness.
     * */
    val variants = mapOf(
        false to mapOf(
            CellPartConnectionMode.Planar to hubConnectionPlanar,
            CellPartConnectionMode.Inner to hubConnectionInner,
            CellPartConnectionMode.Wrapped to hubConnectionWrapped
        ),
        true to mapOf(
            CellPartConnectionMode.Planar to fullConnectionPlanar,
            CellPartConnectionMode.Inner to fullConnectionInner,
            CellPartConnectionMode.Wrapped to fullConnectionWrapped
        )
    )

    val hub = WireConnectionModelPartial(hubConnectionPlanar, hubConnectionInner, hubConnectionWrapped)
}

open class BasicPartVisual<P : Part>(
    ctx: MultipartVisualizationContext,
    part: P,
    model: PartialModel,
    scale: Vector3d = Vector3d.one,
    rotation: Double = 0.0,
    smoothLighting: Boolean = false
) : AbstractPartVisual<P>(ctx, part) {
    private val instance = ctx.instancerProvider()
        .instancer(InstanceTypes.TRANSFORMED, if(smoothLighting) PartialModelHelper.applyMaterial(model, FlwMaterials.SMOOTH_LIT) else Models.partial(model))
        .createInstance()
        .also { it.partTransformation(ctx.parent, part, scale, rotation) }

    override fun updateLight(partialTick: Float) {
        visualizationContext.parent.relightInstances(instance)
    }

    override fun _delete() {
        instance.delete()
    }
}

open class BasicSpecVisual<S : Spec>(
    ctx: SpecVisualizationContext,
    spec: S,
    model: PartialModel,
    scale: Vector3d = Vector3d.one,
    rotation: Double = 0.0
) : AbstractSpecVisual<S>(ctx, spec) {
    private val instance = ctx.instancerProvider()
        .instancer(InstanceTypes.TRANSFORMED, Models.partial(model))
        .createInstance()
        .also { it.specTransformation(ctx.parent, spec, scale, rotation) }

    override fun updateLight(p0: Float) {
        visualizationContext.grandparent.relightInstances(instance)
    }

    override fun _delete() {
        instance.delete()
    }
}

interface ConnectedPartRenderState {
    val version: Int
    val connections: IntArray
}

class ConnectedPartRenderStateImpl : ConnectedPartRenderState {
    override var version = 0
        private set

    override var connections = IntArray(0)
        private set

    fun set(connections: IntArray) {
        this.connections = connections
        version++
    }

    companion object {
        fun createIfApplicable(part: Part) = if(part.placement.level.isClientSide) {
            ConnectedPartRenderStateImpl()
        } else {
            null
        }
    }
}

fun<T> T.getConnectedPartsFromTag(tag: CompoundTag): IntArray where T : Part, T : ConnectedPart {
    return if(tag.contains("connections")) {
        tag.getIntArray("connections")
    }
    else {
        IntArray(0)
    }
}

/**
 * Represents a part that forms connections with neighbor game objects, used by the [ConnectedPartVisual].
 * */
interface ConnectedPart {
    @ClientOnly
    val connectedRenderState: ConnectedPartRenderState

    companion object {
        fun pack(part: CellPart<*>, tag: CompoundTag) {
            val values = IntArrayList(2)

            for (remoteCell in part.cell.connections) {
                if(remoteCell is GridConnectionCell) {
                    continue
                }

                val solution = getPartConnectionAsContactSectionConnectionOrNull(part.cell, remoteCell)
                    ?: continue

                values.add(solution.value)
            }

            tag.putIntArray("connections", values)
        }

        fun pack(part: CellPart<*>) = CompoundTag().also {
            pack(part, it)
        }
    }
}

open class ConnectedPartVisual<P>(
    ctx: MultipartVisualizationContext,
    part: P,
    body: PartialModel,
    val connectionModels: Map<Base6Direction3d, WireConnectionModelPartial>
) : AbstractPartVisual<P>(ctx, part), SimpleDynamicVisual where P : Part, P : ConnectedPart {
    constructor(ctx: MultipartVisualizationContext, part: P, body: PartialModel, connection: WireConnectionModelPartial) : this(
        ctx,
        part,
        body,
        mapOf(
            Base6Direction3d.Front to connection,
            Base6Direction3d.Back to connection,
            Base6Direction3d.Left to connection,
            Base6Direction3d.Right to connection
        )
    )

    constructor(ctx: MultipartVisualizationContext, part: P, body: PartialModel, connection: WireConnectionModel) : this(
        ctx,
        part,
        body,
        connection.hub
    )

    val bodyInstance: TransformedInstance = visualizationContext.instancerProvider()
        .instancer(InstanceTypes.TRANSFORMED, Models.partial(body))
        .createInstance()
        .also { it.partTransformation(visualizationContext.parent, part) }

    private val connectionInstances = Int2ObjectOpenHashMap<TransformedInstance>()

    /**
     * Tracks the last acknowledged version of the connections from [WirePart.RenderState].
     * */
    private var connectionsVersion = -1

    override fun beginFrame(ctx: DynamicVisual.Context) {
        val partialTick = ctx.partialTick()
        val partRenderState = part.connectedRenderState

        val latestConnectionsVersion = partRenderState.version
        if(connectionsVersion != latestConnectionsVersion) {
            connectionsVersion = latestConnectionsVersion
            applyConnectionData(partRenderState.connections, partialTick)
        }
    }

    private fun applyConnectionData(values: IntArray, partialTick: Float) {
        deleteConnectionInstances()

        for (value in values) {
            val info = PartConnectionRenderInfo(value)
            val direction = info.directionPart

            val model = connectionModels[direction]
                ?: continue

            val instance = visualizationContext.instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, model.variants[info.mode]!!.get())
                .createInstance()
                .partTransformation(
                    visualizationContext.parent,
                    part,
                    yRotation = when (direction) {
                        Base6Direction3d.Front -> 0.0
                        Base6Direction3d.Back -> PI
                        Base6Direction3d.Left -> PI / 2.0
                        Base6Direction3d.Right -> -PI / 2.0
                        else -> error("Invalid connected part direction $direction")
                    }
                )

            connectionInstances.putUnique(value, instance)
        }

        updateLight(partialTick)
    }

    override fun updateLight(partialTick: Float) {
        visualizationContext.parent.relightInstances(bodyInstance)
        visualizationContext.parent.relightInstances(connectionInstances.values)
    }

    override fun _delete() {
        bodyInstance.delete()
        deleteConnectionInstances()
    }

    private fun deleteConnectionInstances() {
        for (instance in connectionInstances.values) {
            instance.delete()
        }

        connectionInstances.clear()
    }
}

interface BasicKineticPart {
    val renderState: RenderState?

    interface RenderState {
        val version: Int
        val angle: Double
        val angularVelocity: Double
    }

    class RenderStateImpl : RenderState {
        override var version = 0
            private set

        override var angle = 0.0
            private set

        override var angularVelocity = 0.0
            private set

        fun load(packet: RotatingKineticState) {
            angle = packet.angle
            angularVelocity = packet.angularVelocity
            version++
        }

        companion object {
            fun createFor(part: Part) = if(part.placement.level.isClientSide) {
                RenderStateImpl()
            }
            else null
        }
    }
}

class BasicKineticPartVisual<P>(
    visualizationContext: MultipartVisualizationContext,
    part: P,
    body: PartialModel,
    shaft: PartialModel
) : AbstractPartVisual<P>(visualizationContext, part), SimpleDynamicVisual, ShaderLightVisual where P : Part, P : BasicKineticPart {
    val body: TransformedInstance = visualizationContext.instancerProvider()
        .instancer(InstanceTypes.TRANSFORMED, Models.partial(body))
        .createInstance()
        .also { it.partTransformation(visualizationContext.parent, part) }

    val shaftCenter = FlwModels
        .getModelCenter(shaft)

    val shaft: TransformedInstance = visualizationContext.instancerProvider()
        .instancer(InstanceTypes.TRANSFORMED, PartialModelHelper.applyMaterial(shaft, FlwMaterials.SMOOTH_LIT))
        .createInstance()

    var version = 0
    val interpolator = KineticInterpolatorClient()

    private fun poseShaft() {
        val x = shaftCenter.x
        val y = shaftCenter.y

        shaft.setIdentityTransform()
            .partTransformation(visualizationContext.parent, part)
            .translate(x, y, 0.0)
            .rotateZ(interpolator.clientRotation.ln().toFloat())
            .translate(-x, -y, 0.0)
            .handle()
            .setChanged()
    }

    init {
        poseShaft()
    }

    override fun beginFrame(p0: DynamicVisual.Context?) {
        val renderState = part.renderState!!

        val targetVersion = renderState.version
        if(version != targetVersion) {
            version = targetVersion
            interpolator.applyServerState(renderState.angle, renderState.angularVelocity)
        }

        interpolator.update()
        poseShaft()
    }

    override fun updateLight(p0: Float) {
        visualizationContext.parent.relightInstances(body)
    }

    override fun _delete() {
        body.delete()
        shaft.delete()
    }
}

/**
 * @param model The model for the shaft.
 * @param axis The axis to rotate around.
 * @param factor The model is rotated by `angle * [factor]`.
 * */
data class ShaftDescription(
    val model: PartialModel,
    val axis: Axis3d,
    val factor: Double
)

/**
 * Visual meant for a part with one single kinetic node, but multiple shaft outputs (e.g. a rigid coupling).
 * */
class SingleNodeMultiShaftKineticPartVisual<P>(
    visualizationContext: MultipartVisualizationContext,
    part: P,
    body: PartialModel,
    shaftDescriptions: List<ShaftDescription>
) : AbstractPartVisual<P>(visualizationContext, part), SimpleDynamicVisual, ShaderLightVisual where P : Part, P : BasicKineticPart {
    class ShaftInstance(
        val description: ShaftDescription,
        val instance: TransformedInstance,
        val center: Vector3d
    )

    val body: TransformedInstance = visualizationContext.instancerProvider()
        .instancer(InstanceTypes.TRANSFORMED, Models.partial(body))
        .createInstance()
        .also { it.partTransformation(visualizationContext.parent, part) }

    val shafts = shaftDescriptions.map {
        val instance = visualizationContext.instancerProvider()
            .instancer(InstanceTypes.TRANSFORMED, PartialModelHelper.applyMaterial(it.model, FlwMaterials.SMOOTH_LIT))
            .createInstance()

        val center = FlwModels.getModelCenter(it.model)

        ShaftInstance(it, instance, center)
    }

    var version = 0
    val interpolator = KineticInterpolatorClient()

    private fun poseShafts() {
        val t = interpolator.clientRotation.ln()

        shafts.forEach {
            val angle = (t * it.description.factor).toFloat()

            val x: Double
            val y: Double
            val z: Double
            val q: Quaternionf

            when(it.description.axis) {
                Axis3d.X -> {
                    x = 0.0
                    y = it.center.y
                    z = it.center.z
                    q = Quaternionf().rotateX(angle)
                }
                Axis3d.Y -> {
                    x = it.center.x
                    y = 0.0
                    z = it.center.z
                    q = Quaternionf().rotateY(angle)
                }
                Axis3d.Z -> {
                    x = it.center.x
                    y = it.center.y
                    z = 0.0
                    q = Quaternionf().rotateZ(angle)
                }
            }

            it.instance.setIdentityTransform()
                .partTransformation(visualizationContext.parent, part)
                .translate(x, y, z)
                .rotate(q)
                .translate(-x, -y, -z)
                .setChanged()
        }
    }

    init {
        poseShafts()
    }

    override fun beginFrame(p0: DynamicVisual.Context?) {
        val renderState = part.renderState!!

        val targetVersion = renderState.version
        if(version != targetVersion) {
            version = targetVersion
            interpolator.applyServerState(renderState.angle, renderState.angularVelocity)
        }

        interpolator.update()

        poseShafts()
    }

    override fun updateLight(p0: Float) {
        visualizationContext.parent.relightInstances(body)
    }

    override fun _delete() {
        body.delete()

        shafts.forEach {
            it.instance.delete()
        }
    }
}

val partOffsetTable = buildDirectionTable {
    when (it) {
        Direction.DOWN -> Vector3d(0.5, 1.0, 0.5)
        Direction.UP -> Vector3d(0.5, 0.0, 0.5)
        Direction.NORTH -> Vector3d(0.5, 0.5, 1.0)
        Direction.SOUTH -> Vector3d(0.5, 0.5, 0.0)
        Direction.WEST -> Vector3d(1.0, 0.5, 0.5)
        Direction.EAST -> Vector3d(0.0, 0.5, 0.5)
    }
}

fun<T : Affine<T>> T.partTransformation(parent: MultipartBlockEntityVisual, part: Part, scale: Vector3d = Vector3d.one, yRotation: Double = 0.0): T {
    val (dx, dy, dz) = partOffsetTable[part.placement.face.get3DDataValue()]

    return this
        .translate(parent.visualPosition)
        .translate(dx, dy, dz)
        .rotate(part.placement.face.rotationFast)
        .rotateY((yRotation + part.placement.facing.angle).toFloat())
        .scale(scale.x.toFloat(), scale.y.toFloat(), scale.z.toFloat())
        .translate(-0.5, 0.0, -0.5)
}


fun<T : Affine<T>> T.specTransformation(parent: SpecContainerPartVisual, spec: Spec, scale: Vector3d = Vector3d.one, yRotation: Double = 0.0): T {
    val (dx, dy, dz) = partOffsetTable[parent.part.placement.face.get3DDataValue()]
    val (dx1, dy1, dz1) = spec.placement.mountingPointWorld - parent.part.placement.mountingPointWorld

    return this
        .translate(parent.visualizationContext.parent.visualPosition)
        .translate(dx + dx1, dy + dy1, dz + dz1)
        .rotate(parent.part.placement.face.rotationFast)
        .rotateY((yRotation + parent.part.placement.facing.angle + spec.placement.orientation.ln()).toFloat())
        .scale(scale.x.toFloat(), scale.y.toFloat(), scale.z.toFloat())
        .translate(-0.5, 0.0, -0.5)
}

class KnobMap(val changedNotifier: (() -> Unit)?) {
    /**
     * Holds optional configuration for a [Knob], such as bounds and screwdriver-settable metadata.
     * */
    class KnobConfiguration {
        var lowerBound: Double? = null
            private set

        var upperBound: Double? = null
            private set

        var interactableTranslationKey: String? = null
            private set

        fun setLowerBound(value: Double) {
            lowerBound = value
        }

        fun setUpperBound(value: Double) {
            upperBound = value
        }

        fun setLimits(lower: Double, upper: Double) {
            lowerBound = lower
            upperBound = upper
        }

        fun makeInteractable(translationKey: String) {
            interactableTranslationKey = translationKey
        }

        fun clamp(value: Double): Double {
            val lo = lowerBound
            val hi = upperBound
            return when {
                lo != null && hi != null -> value.coerceIn(lo, hi)
                lo != null -> value.coerceAtLeast(lo)
                hi != null -> value.coerceAtMost(hi)
                else -> value
            }
        }
    }

    /**
     * Holds the rotation state of a knob ([rotation]).
     * @param model The partial model. Safe to exist on the server side as long as the underlying data isn't accessed.
     * @param knobId The ID assigned by the [KnobMap] for synchronization and other operations.
     * */
    class Knob(
        val model: PartialModel,
        val rotationAxis: Vector3d,
        val knobId: Int,
        val map: KnobMap,
        val descriptor: String,
        initialRotation: Double,
        private val boundingBoxSupplier: (Double) -> OrientedBoundingBox3d
    ) {
        var configuration: KnobConfiguration? = null
            private set

        /**
         * Configures this knob's bounds and interactability.
         * */
        fun configure(action: KnobConfiguration.() -> Unit): Knob {
            val config = configuration ?: KnobConfiguration().also { configuration = it }
            config.action()
            return this
        }

        val isInteractable: Boolean get() = configuration?.interactableTranslationKey != null

        /**
         * The turn state of the knob. Changing the value will mark the state as changed, for synchronization.
         * If bounds are configured, the value is clamped to them.
         * */
        var rotation : Double = initialRotation
            set(value) {
                val clamped = configuration?.clamp(value) ?: value
                if(field != clamped) {
                    field = clamped
                    map.knobChanged()
                }
            }

        fun computeOBB() = boundingBoxSupplier(rotation)
    }

    /**
     * Synchronization packet for bulk part messages. Contains the new knob states in order.
     * */
    data class SyncPacket(val newStates: DoubleArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as SyncPacket

            return newStates.contentEquals(other.newStates)
        }

        override fun hashCode(): Int {
            return newStates.contentHashCode()
        }

        companion object {
            fun serialize(packet: SyncPacket, buffer: FriendlyByteBuf) {
                buffer.writeDoubleArray(packet.newStates)
            }

            fun deserialize(buffer: FriendlyByteBuf) = SyncPacket(
                buffer.readDoubleArray()
            )
        }
    }

    private var mapInternal = ArrayList<Knob>()
    private var loading = false

    val knobs: List<Knob> get() = mapInternal

    /**
     * Incremented when knob states are changed.
     * Check against this for your rendering.
     * */
    var version = 0
        private set

    /**
     * Registers a knob.
     * @param model The model used by the visual.
     * @param defaultState The default state of the knob.
     * Important for schema changes: if the number of knobs, or the descriptor of a knob doesn't correspond with the saved data, the data is discarded.
     * */
    fun addKnobBB(
        part: Part,
        model: PartialModel, axis: Vector3d, descriptor: String,
        bbX: Double, bbY: Double, bbZ: Double,
        bbSX: Double, bbSY: Double, bbSZ: Double,
        defaultState: Double = 0.0
    ) : Knob {
        val size = Vector3d(bbSX / 16.0, bbSY / 16.0, bbSZ / 16.0)

        val box = BoundingBox3d.fromCenterSize(((Vector3d(
            bbX / 16.0,
            bbY / 16.0,
            bbZ / 16.0
        )) - Vector3d.one * maskXY / 2.0) + size / 2.0, size)

        val center = box.center
        val supplier: (Double) -> OrientedBoundingBox3d = {
            SpecGeometry.boundingBox(
                part.placement.mountingPointWorld +
                    part.placement.positiveX.vector3d * center.x +
                    part.placement.positiveY.vector3d * center.y +
                    part.placement.positiveZ.vector3d * center.z,
                Rotation2d.exp(
                    it
                ) * part.placement.facing.rotation2d,
                Vector3d(size.x, size.y, size.z),
                part.placement.facing,
                part.placement.face
            )
        }

        val result = Knob(
            model,
            axis,
            mapInternal.size,
            this,
            descriptor,
            defaultState,
            supplier
        )

        mapInternal.add(result)
        return result
    }

    private fun knobChanged() {
        ++version

        if(!loading) {
            changedNotifier?.invoke()
        }
    }

    /**
     * Call when multiple knobs need to be changed (inside the [block]).
     * A single update to the [changedNotifier] is sent at the end.
     * */
    @OptIn(ExperimentalContracts::class)
    fun loadChanges(block: () -> Unit) {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }

        loading = true
        block()
        loading = false
        ++version
        changedNotifier?.invoke()
    }

    /**
     * Finds the knob the player is looking at, or null if none.
     * */
    @ServerOnly
    fun pickKnob(player: ServerPlayer) : Knob? {
        requireIsOnServerThread {
            "Tried to pick knob on non-server side"
        }

        val ray = player.getViewRay()

        var minDistance = Double.POSITIVE_INFINITY
        var result: Knob? = null

        for (it in mapInternal) {
            val obb = it.computeOBB()

            val intersection = ray intersectionWith obb

            if(intersection != null) {
                val t = intersection.entry

                if(t > 0.0) {
                    if(t < minDistance) {
                        minDistance = t
                        result = it
                    }
                }
            }
        }

        return result
    }

    /**
     * Handles rotating a knob using the screwdriver scroll.
     * */
    @ServerOnly
    fun screwdriverInteraction(player: ServerPlayer, delta: Double) : Boolean {
        val knob = pickKnob(player)

        if(knob != null) {
            knob.rotation += delta
            return true
        }

        return false
    }

    /**
     * Handles setting a knob to an exact value using the screwdriver.
     * If [value] is empty, sends the player a chat message with the knob name (if interactable).
     * If [value] is present, clamps and applies it, then sends a confirmation chat message.
     * Returns false if no interactable knob was found.
     * */
    @ServerOnly
    fun screwdriverConfigure(player: ServerPlayer, value: OptionalDouble) : Boolean {
        val knob = pickKnob(player) ?: return false

        val key = knob.configuration?.interactableTranslationKey ?: return false

        if(!value.isPresent) {
            player.sendSystemMessage(Component.translatable(key))
            return true
        }

        val raw = value.unwrap()
        val clamped = knob.configuration?.clamp(raw) ?: raw
        knob.rotation = clamped

        player.sendSystemMessage(
            Component.translatable(key).append(": ").append(String.format("%.3f", clamped))
        )

        return true
    }

    fun getSyncPacket() : SyncPacket {
        val values = DoubleArray(mapInternal.size)

        for (i in 0 until mapInternal.size) {
            values[i] = mapInternal[i].rotation
        }

        return SyncPacket(values)
    }

    fun loadSyncPacket(packet: SyncPacket) {
        require(packet.newStates.size == mapInternal.size) {
            "Received ${packet.newStates.size} knob states, but map had ${mapInternal.size} states"
        }

        ++version

        loading = true

        for (i in 0 until mapInternal.size) {
            mapInternal[i].rotation = packet.newStates[i]
        }

        loading = false
    }

    /**
     * Saves the knobs to NBT.
     * */
    fun getSaveTag() = CompoundTag().also {
        val list = ListTag()

        mapInternal.forEach { knob ->
            val knobTag = CompoundTag()

            knobTag.putString(DESCRIPTOR, knob.descriptor)
            knobTag.putDouble(STATE, knob.rotation)

            list.add(knobTag)
        }

        it.put(KNOB_LIST, list)
    }

    fun loadFromTag(tag: CompoundTag) {
        ++version // regardless of discard
        loading = true
        loadFromTagCore(tag)
        loading = false
    }

    private fun loadFromTagCore(tag: CompoundTag) {
        val expectedCount = mapInternal.size

        val list = tag.getListTag(KNOB_LIST)

        if(list.size != expectedCount) {
            LOG.error("Knob schema change: " +
                "knobs ${mapInternal.joinToString(", ") { it.descriptor }} (count $expectedCount) " +
                "were previously ${list.size} in count. Discarding!"
            )

            return
        }

        val loadedStates = DoubleArray(expectedCount)

        for (i in 0 until expectedCount) {
            val knobTag = list[i] as CompoundTag

            val descriptor = knobTag.getString(DESCRIPTOR)
            val expectedDescriptor = mapInternal[i].descriptor

            if(descriptor != expectedDescriptor) {
                LOG.error("Knob schema change: knob $i ($expectedDescriptor) was previously $descriptor. Discarding!")
                return
            }

            val state = knobTag.getDouble(STATE)

            loadedStates[i] = state
        }

        for (i in 0 until expectedCount) {
            mapInternal[i].rotation = loadedStates[i]
        }
    }

    companion object {
        private const val KNOB_LIST = "knobMap"
        private const val DESCRIPTOR = "descriptor"
        private const val STATE = "state"

    }
}

interface PartWithKnobs {
    /**
     * Gets the knob map, on both the server and the client.
     * The map must be defined exactly the same on both sides, otherwise you're fucked.
     * */
    val knobMap: KnobMap
}

class PartWithKnobsVisual<T>(
    ctx: MultipartVisualizationContext,
    part: T,
    bodyModel: PartialModel
) : AbstractPartVisual<T>(ctx, part), SimpleDynamicVisual where T : Part, T : PartWithKnobs {
    companion object {
        fun <T> createKnobList(ctx: MultipartVisualizationContext, part: T) : ArrayList<KnobData> where T : Part, T : PartWithKnobs {
            val knobs = ArrayList<KnobData>()

            part.knobMap.knobs.forEach { knob ->
                val instance = ctx.instancerProvider()
                    .instancer(InstanceTypes.TRANSFORMED, Models.partial(knob.model))
                    .createInstance()

                val (offsetX, offsetY, offsetZ) = FlwModels
                    .getModelCenter(knob.model)
                    .projectOnPlane(knob.rotationAxis)

                knobs.add(
                    KnobData(
                        instance,
                        knob,
                        offsetX.toFloat(), offsetY.toFloat(), offsetZ.toFloat()
                    )
                )
            }

            return knobs
        }

        fun<T> transformKnobList(ctx: MultipartVisualizationContext, part: T, knobs: ArrayList<KnobData>) where T : Part, T : PartWithKnobs {
            knobs.forEach { (instance, target, offsetX, offsetY, offsetZ) ->
                instance.setIdentityTransform()
                    .partTransformation(ctx.parent, part)
                    .translate(offsetX, offsetY, offsetZ)
                    .rotate(Rotation3d.exp(target.rotationAxis * target.rotation).cast())
                    .translate(-offsetX, -offsetY, -offsetZ)
                    .handle()
                    .setChanged()
            }
        }
    }

    private val body = ctx.instancerProvider()
        .instancer(InstanceTypes.TRANSFORMED, Models.partial(bodyModel))
        .createInstance()
        .also { it.partTransformation(ctx.parent, part, Vector3d.one, 0.0) }

    data class KnobData(
        val instance: TransformedInstance,
        val target: KnobMap.Knob,
        val offsetX: Float,
        val offsetY: Float,
        val offsetZ: Float
    )

    private val knobs = createKnobList(ctx, part)
    private var version = 0

    init {
        transformKnobs()
    }

    private fun transformKnobs() {
        transformKnobList(visualizationContext, part, knobs)
    }

    override fun beginFrame(p0: DynamicVisual.Context?) {
        val targetVersion = part.knobMap.version

        if(version != targetVersion) {
            version = targetVersion
            transformKnobs()
        }
    }

    override fun updateLight(partialTick: Float) {
        visualizationContext.parent.relightInstances(body)
        visualizationContext.parent.relightInstances(knobs.map { it.instance })
    }

    override fun _delete() {
        body.delete()
        knobs.forEach { it.instance.delete() }
    }
}

open class ConnectedPartWithKnobsVisual<P>(
    ctx: MultipartVisualizationContext,
    part: P,
    body: PartialModel,
    connectionModels: Map<Base6Direction3d, WireConnectionModelPartial>
) : ConnectedPartVisual<P>(ctx, part, body, connectionModels) where P : Part, P : ConnectedPart, P : PartWithKnobs {
    private val knobs = createKnobList(ctx, part)
    private var knobsVersion = 0

    init {
        transformKnobs()
    }

    private fun transformKnobs() {
        transformKnobList(visualizationContext, part, knobs)
    }

    override fun beginFrame(ctx: DynamicVisual.Context) {
        super.beginFrame(ctx)

        val targetVersion = part.knobMap.version
        if(knobsVersion != targetVersion) {
            knobsVersion = targetVersion
            transformKnobs()
        }
    }

    override fun updateLight(partialTick: Float) {
        super.updateLight(partialTick)
        visualizationContext.parent.relightInstances(knobs.map { it.instance })
    }

    override fun _delete() {
        super._delete()
        knobs.forEach { it.instance.delete() }
    }
}

class TestBlockEntityVisual<T : BlockEntity>(
    ctx: VisualizationContext,
    blockEntity: T,
    partialTick: Float,
    model: PartialModel,
    transformer: (instance: TransformedInstance, visual: TestBlockEntityVisual<T>) -> Unit,
) : AbstractBlockEntityVisual<T>(ctx, blockEntity, partialTick) {
    var instance: TransformedInstance = visualizationContext.instancerProvider()
        .instancer(InstanceTypes.TRANSFORMED, PartialModelHelper.applyMaterial(model, FlwMaterials.OIT_NON_MIP_SMOOTH_LIT))
        .createInstance()
        .also { transformer(it, this) }

    override fun updateLight(partialTick: Float) {
        relight(instance)
    }

    override fun collectCrumblingInstances(consumer: Consumer<Instance?>) {
        consumer.accept(instance)
    }

    override fun _delete() {
        instance.delete()
    }
}

class SimpleBigBlockEntityVisual<TBlockEntity>(
    ctx: VisualizationContext,
    blockEntity: TBlockEntity,
    partialTick: Float,
    model: PartialModel,
    material: SimpleMaterial = FlwMaterials.SMOOTH_LIT
) : AbstractBlockEntityVisual<TBlockEntity>(ctx, blockEntity, partialTick), ShaderLightVisual where TBlockEntity : BigBlockRepresentativeBlockEntity<TBlockEntity>, TBlockEntity : BlockEntity {
    val body: TransformedInstance = visualizationContext.instancerProvider()
        .instancer(InstanceTypes.TRANSFORMED, PartialModelHelper.applyMaterial(model, material))
        .createInstance()
        .also {
            it.translate(visualPosition)
            it.center()
            it.rotateToFace(blockEntity.representativeFacing.opposite)
            it.uncenter()
        }

    override fun updateLight(p0: Float) {
        // NOOP (shader light)
    }

    override fun setSectionCollector(sectionCollector: SectionTrackedVisual.SectionCollector) {
        this.lightSections = sectionCollector

        sectionCollector.sections(
            blockEntity.delegateMap.getTotalSpannedSectionsFat(
                blockState.getValue(HorizontalDirectionalBlock.FACING),
                pos
            )
        )
    }

    override fun collectCrumblingInstances(p0: Consumer<Instance?>) {
        p0.accept(body)
    }

    override fun _delete() {
        body.delete()
    }
}
