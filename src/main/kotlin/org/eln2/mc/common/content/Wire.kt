@file:Suppress("NonAsciiCharacters", "MemberVisibilityCanBePrivate")

package org.eln2.mc.common.content

import com.jozufozu.flywheel.backend.Backend
import com.jozufozu.flywheel.config.BackendType
import com.jozufozu.flywheel.core.Materials
import com.jozufozu.flywheel.core.PartialModel
import com.jozufozu.flywheel.core.materials.model.ModelData
import com.jozufozu.flywheel.util.Color
import com.jozufozu.flywheel.util.transform.Transform
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import kotlinx.serialization.Serializable
import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.LightLayer
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.fml.DistExecutor
import net.minecraftforge.registries.RegistryObject
import org.ageseries.libage.sim.Material
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.thermal.*
import org.eln2.mc.*
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.foundation.*
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.events.AtomicUpdate
import org.eln2.mc.common.network.serverToClient.PacketHandlerBuilder
import org.eln2.mc.common.parts.PartRegistry
import org.eln2.mc.common.parts.foundation.*
import org.eln2.mc.data.*
import org.eln2.mc.integration.WailaNode
import org.eln2.mc.integration.WailaTooltipBuilder
import org.eln2.mc.mathematics.*
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.util.function.Supplier
import kotlin.math.PI
import kotlin.math.max

/**
 * Generalized thermal conductor, in the form of a single thermal body that gets connected to all neighbor cells.
 * */
class ThermalWireObject(cell: Cell, thermalDefinition: ThermalBodyDef) : ThermalObject<Cell>(cell), PersistentObject, ThermalContactInfo {
    companion object {
        // Storing temperature. If I change the properties of the material, it will be the same temperature in game.
        private const val TEMPERATURE = "temperature"
    }

    constructor(cell: Cell) : this(
        cell,
        ThermalBodyDef(
            Material.COPPER,
            mass = 1.0,
            area = cylinderSurfaceArea(1.0, 0.05)
        )
    )

    var thermalBody = thermalDefinition.create().also { body ->
        if (thermalDefinition.energy == null) {
            cell.environmentData.getOrNull<EnvironmentalTemperatureField>()?.readInto(body)
        }
    }

    fun readTemperature(): Double = thermalBody.thermal.temperature.kelvin

    override fun offerComponent(neighbour: ThermalObject<*>) = ThermalComponentInfo(thermalBody)

    override fun addComponents(simulator: Simulator) {
        simulator.add(thermalBody)

        // Connect to environment if it has a temperature and thermal conductivity:

        val temperature = cell.environmentData.getOrNull<EnvironmentalTemperatureField>()?.readTemperature() ?: return
        val thermalConductivity = cell.environmentData.getOrNull<EnvironmentalThermalConductivityField>()?.readConductivity() ?: return

        simulator.connect(thermalBody, EnvironmentInformation(temperature, thermalConductivity))
    }

    override fun saveObjectNbt(): CompoundTag {
        return CompoundTag().also {
            it.putDouble(TEMPERATURE, thermalBody.temperatureKelvin)
        }
    }

    override fun loadObjectNbt(tag: CompoundTag) {
        if(tag.contains(TEMPERATURE)) {
            thermalBody.temperatureKelvin = tag.getDouble(TEMPERATURE)
        }
    }

    override fun getContactTemperature(other: Locator) = readTemperature()
}

/**
 * Generalized electrical wire, created by joining the "internal" pins of a [ResistorBundle].
 * The "external" pins are offered to other cells.
 * */
class ElectricalWireObject(cell: Cell) : ElectricalObject<Cell>(cell) {
    private val resistors = ResistorBundle(0.05, this)

    val totalPower get() = resistors.totalPower

    /**
     * Gets or sets the resistance of the bundle.
     * Only applied when the circuit is re-built.
     * */
    var resistance: Double
        get() = resistors.resistance * 2.0
        set(value) { resistors.resistance = value / 2.0 }

    override fun offerComponent(neighbour: ElectricalObject<*>) = resistors.getOfferedResistor(neighbour)

    override fun clearComponents() = resistors.clear()

    override fun addComponents(circuit: Circuit) = resistors.register(connections, circuit)

    override fun build() {
        // The Wire uses a bundle of 4 resistors. Every resistor's "Internal Pin" is connected to every
        // other resistor's internal pin. "External Pins" are offered to connection candidates:

        resistors.connect(connections, this)

        resistors.forEach { a ->
            resistors.forEach { b ->
                if (a != b) {
                    a.connect(INTERNAL_PIN, b, INTERNAL_PIN)
                }
            }
        }
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
}

/**
 * Holds all data required to render a wire connection.
 * @param hub Hub (junction) model
 * @param connection The connection models
 * */
data class WireRenderInfo(
    val hub: PartialModel,
    val connection: WireConnectionModel,
    val tintColor: ThermalTint,
)

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

class WirePolarPatchModel(modelLocation: ResourceLocation, val patchType: WirePatchType) : PolarModel(modelLocation) {
    override fun set(bakedModelSource: BakedModel) {
        super.set(bakedModelSource)

        applyChanges() // the super set the bound model as the field, so we will mutate that in applyChanges
    }

    private fun applyChanges() {
        val bakedModelSource = this.bakedModel

        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        val quads = bakedModelSource.getQuads(null, null, null).map {
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
    }
}

/**
 * Holds information regarding a registered thermal wire.
 * @param thermalProperties The strictly thermal properties of the wire.
 * @param id The ID of the wire cell.
 * */
data class ThermalWireRegistryObject(
    val thermalProperties: WireThermalProperties,
    val id: ResourceLocation,
)

/**
 * Holds information regarding a registered electrical-thermal wire.
 * @param thermalProperties The strictly thermal properties of the wire.
 * @param electricalProperties The strictly electrical properties of the wire.
 * @param id The ID of the wire cell.
 * */
data class ElectricalWireRegistryObject(
    val thermalProperties: WireThermalProperties,
    val electricalProperties: WireElectricalProperties,
    val id: ResourceLocation,
)

abstract class WireBuilder<C : WireCell>(val id: String) {
    var material = ThermalBodyDef(Material.COPPER, 1.0, cylinderSurfaceArea(1.0, 0.05))
    var contactSurfaceArea = PI * (0.05 * 0.05)
    var damageOptions = TemperatureExplosionBehaviorOptions()
    var replicatesInternalTemperature = true
    var isIncandescent: Boolean = true
    var hubSize = Vector3d(3.5 / 16.0, 2.0 / 16.0, 3.5 / 16.0)
    var tint = defaultRadiantBodyColor()
    var smokeTemperature: Double? = null
    private var renderInfo: Supplier<WireRenderInfo>? = null
    var connectionSize = Vector3d(2.0 / 16.0, 1.5 / 16.0, 6.25 / 16.0)
    var wireShapes : Map<Pair<FaceLocator, Direction>, VoxelShape>? = null
    var wireShapesFilled : Map<Pair<FaceLocator, Direction>, VoxelShape>? = null

    fun renderer(supplier: Supplier<WireRenderInfo>) {
        DistExecutor.safeRunWhenOn(Dist.CLIENT) {
            DistExecutor.SafeRunnable {
                LOG.info("Set up wire render")
                renderInfo = supplier
            }
        }
    }

    protected abstract fun defaultRender() : WireRenderInfo

    protected fun createMaterialProperties() = WireThermalProperties(material, damageOptions, replicatesInternalTemperature, isIncandescent)

    protected fun registerPart(properties: WireThermalProperties, provider: RegistryObject<CellProvider<C>>) {
        fun createShapes(size: Vector3d) : HashMap<Pair<FaceLocator, Direction>, VoxelShape> {
            val results = HashMap<Pair<FaceLocator, Direction>, VoxelShape>()

            val boundingBox = AABB(
                (-size / 2.0).toVec3(),
                (+size / 2.0).toVec3()
            ).move((-Vector3d.unitZ / 2.0 + Vector3d.unitZ * (size.z / 2.0)).toVec3())

            Base6Direction3dMask.FULL.directionList.forEach { face ->
                Base6Direction3dMask.HORIZONTALS.directionList.forEach { facing ->
                    results[
                        Pair(
                            face,
                            incrementFromForwardUp(
                                facing,
                                face,
                                Direction.NORTH
                            )
                        )
                    ] = Shapes.create(
                        PartGeometry.transform(
                            boundingBox,
                            facing,
                            face
                        )
                    )
                }
            }

            return results
        }

        val connections = wireShapes ?: createShapes(connectionSize)
        val connectionsFilled = wireShapesFilled ?: createShapes(Vector3d(connectionSize.x, connectionSize.y, 0.5))

        val renderInfo = this.renderInfo ?: Supplier {
            defaultRender()
        }

        val smokeTemperature = this.smokeTemperature ?: (!properties.damageOptions.temperatureThreshold * 0.9)

        PartRegistry.part(
            id,
            BasicPartProvider({ ci ->
                WirePart(
                    ci,
                    cellProvider = provider.get(),
                    isIncandescent,
                    smokeTemperature,
                    connections,
                    connectionsFilled,
                    renderInfo = renderInfo
                )
            }, hubSize)
        )
    }
}

class ThermalWireBuilder(id: String) : WireBuilder<ThermalWireCell>(id) {
    fun register(): ThermalWireRegistryObject {
        val material = createMaterialProperties()
        val cell = CellRegistry.cell(
            id,
            BasicCellProvider { ci ->
                ThermalWireCell(
                    ci,
                    contactSurfaceArea,
                    material
                )
            }
        )
        registerPart(material, cell)
        return ThermalWireRegistryObject(material, cell.id)
    }

    override fun defaultRender() = WireRenderInfo(
        PartialModels.THERMAL_WIRE_HUB,
        PartialModels.THERMAL_WIRE_CONNECTION,
        tint
    )
}

class ElectricalWireBuilder(id: String) : WireBuilder<ElectricalWireCell>(id) {
    var resistance: Double = 2.14 * 1e-5

    fun register(): ElectricalWireRegistryObject {
        val material = createMaterialProperties()
        val electrical = WireElectricalProperties(resistance)
        val cell = CellRegistry.cell(
            id,
            BasicCellProvider { ci ->
                ElectricalWireCell(
                    ci,
                    contactSurfaceArea,
                    material,
                    electrical
                )
            }
        )
        registerPart(material, cell)
        return ElectricalWireRegistryObject(material, electrical, cell.id)
    }

    override fun defaultRender() = WireRenderInfo(
        PartialModels.ELECTRICAL_WIRE_HUB,
        PartialModels.ELECTRICAL_WIRE_CONNECTION,
        tint
    )
}

/**
 * Thermal properties of a wire.
 * @param thermalDef The definition used to create the thermal body of the wire.
 * @param damageOptions The damage config, passed to the [TemperatureExplosionBehavior]
 * @param replicatesInternalTemperature Indicates if the wire should replicate the internal temperature (temperature of the wire's thermal body)
 * @param replicatesExternalTemperature Indicates if the wire should replicate the external temperatures (temperatures of connected thermal objects)
 * */
data class WireThermalProperties(
    val thermalDef: ThermalBodyDef,
    val damageOptions: TemperatureExplosionBehaviorOptions,
    val replicatesInternalTemperature: Boolean,
    val replicatesExternalTemperature: Boolean
)

/**
 * Electrical properties of a wire.
 * @param electricalResistance The electrical resistance.
 * */
data class WireElectricalProperties(
    val electricalResistance: Double
)

interface DirectionBlacklist {
    fun addToBlacklist(directionPart: Base6Direction3d) : Boolean
    fun removeFromBlacklist(directionPart: Base6Direction3d) : Boolean
}

open class WireCell(ci: CellCreateInfo, val connectionCrossSection: Double) : Cell(ci), DirectionBlacklist, CellContactPointSurface {
    companion object {
        private const val BLACKLIST = "blacklist"
    }

    private val blacklist = HashSet<Base6Direction3d>()

    override fun addToBlacklist(directionPart: Base6Direction3d) = blacklist.add(directionPart)

    override fun removeFromBlacklist(directionPart: Base6Direction3d) = blacklist.remove(directionPart)

    override fun saveCellData() = CompoundTag().also { tag ->
        tag.putIntArray(BLACKLIST, blacklist.map { it.id })
    }

    override fun loadCellData(tag: CompoundTag) {
        if(tag.contains(BLACKLIST)) {
            blacklist.addAll(tag.getIntArray(BLACKLIST).map { Base6Direction3d.byId[it] })
        }
    }

    override fun getContactSection(cell: Cell) = connectionCrossSection

    override fun connectionPredicate(remoteCell: Cell): Boolean {
        if(!super.connectionPredicate(remoteCell)) {
            return false
        }

        val solution = getPartConnectionOrNull(this.locator, remoteCell.locator)
            ?: return true

        return !blacklist.contains(solution.directionPart)
    }
}

open class ThermalWireCell(ci: CellCreateInfo, connectionCrossSection: Double, val thermalProperties: WireThermalProperties) : WireCell(ci, connectionCrossSection) {
    @SimObject @Inspect
    val thermalWire = ThermalWireObject(
        self()
    )

    @Behavior
    val explosion = TemperatureExplosionBehavior(
        thermalWire::readTemperature,
        thermalProperties.damageOptions,
        self()
    )

    /**
     * Replicates the temperature of [thermalWire] if [WireThermalProperties.replicatesInternalTemperature]
     * */
    @Replicator
    fun internalTemperatureReplicator(consumer: InternalTemperatureConsumer) =
        if (thermalProperties.replicatesInternalTemperature)
            InternalTemperatureReplicatorBehavior(listOf(thermalWire.thermalBody), consumer)
        else null

    /**
     * Replicates the external temperatures if [WireThermalProperties.replicatesExternalTemperature]
     * */
    @Replicator
    fun externalTemperatureReplicator(consumer: ExternalTemperatureConsumer) =
        if(thermalProperties.replicatesExternalTemperature)
            ExternalTemperatureReplicatorBehavior(this, consumer)
        else null
}


open class ElectricalWireCell(ci: CellCreateInfo, contactCrossSection: Double, thermalProperties: WireThermalProperties, val electricalProperties: WireElectricalProperties) : ThermalWireCell(ci, contactCrossSection, thermalProperties) {
    @SimObject
    val electricalWire = ElectricalWireObject(self()).also {
        it.resistance = electricalProperties.electricalResistance
    }

    @Behavior
    val heater = PowerHeatingBehavior(
        electricalWire::totalPower,
        thermalWire.thermalBody
    )

    override fun connectionPredicate(remoteCell: Cell) =
        remoteCell.hasObject(SimulationObjectType.Electrical) && super.connectionPredicate(remoteCell)
}

class WirePart<C : WireCell>(
    ci: PartCreateInfo,
    cellProvider: CellProvider<C>,
    val isIncandescent: Boolean,
    val smokeTemperature: Double,
    val connectionBounds: Map<Pair<FaceLocator, Direction>, VoxelShape>,
    val connectionBoundsFilled: Map<Pair<FaceLocator, Direction>, VoxelShape>,
    val renderInfo: Supplier<WireRenderInfo>?,
) : CellPart<C, WireRenderer<*>>(ci, cellProvider),
    InternalTemperatureConsumer,
    ExternalTemperatureConsumer,
    AnimatedPart,
    WrenchInteractablePart,
    WailaNode
{
    companion object {
        private const val DIRECTIONS = "directions"
    }

    @ServerOnly
    private fun getConnectionsInfo() : List<Int> {
        check(!placement.level.isClientSide)

        if(!hasCell) {
            return emptyList()
        }

        return cell.connections.mapNotNull {
            getPartConnectionOrNull(cell.locator, it.locator)?.value
        }
    }

    @ServerOnly
    private fun getConnectionShapeKey(connectionInfo: Int) = Pair(
        placement.face,
        incrementFromForwardUp(
            placement.horizontalFacing,
            placement.face,
            PartConnectionDirection(connectionInfo).directionPart
        )
    )

    override fun applyWrench(wrench: WrenchItem, context: UseOnContext): InteractionResult {
        if(!hasCell) {
            return InteractionResult.FAIL
        }

        val connections = getConnectionsInfo()

        val shapeSet = if(checkIsFilledVariant(connections)) {
            connectionBoundsFilled
        }
        else {
            connectionBounds
        }

        val boxes = ArrayList<Pair<AABB, Int>>()

        val x = placement.position.x
        val y = placement.position.y
        val z = placement.position.z

        for (connectionInfo in connections) {
            val shape = shapeSet[getConnectionShapeKey(connectionInfo)]
                ?: continue

            shape.forAllBoxes { x0, y0, z0, x1, y1, z1 ->
                val aabb = AABB(
                    x0 + x, y0 + y, z0 + z,
                    x1 + x, y1 + y, z1 + z
                )

                boxes.add(Pair(aabb, connectionInfo))
            }
        }

        val target = clipScene(context.player!!, { it.first }, boxes)

        if(target != null) {
            if(cell.addToBlacklist(PartConnectionDirection(target.second).directionPart)) {
                CellConnections.retopologize(cell, placement.multipart)
                setSyncDirty()
                return InteractionResult.SUCCESS
            }

            // Kind of weird if it fails, how was the connection here?
            return InteractionResult.FAIL
        }
        else {
            val player = context.player!!

            val hit = modelBoundingBox.viewClipExtra(player, placement.position) ?:
                return InteractionResult.FAIL

            val directionPart = Base6Direction3d.fromForwardUp(
                placement.horizontalFacing,
                placement.face,
                hit.direction
            )

            if(cell.removeFromBlacklist(directionPart)) {
                CellConnections.retopologize(cell, placement.multipart)
                setSyncDirty()
                return InteractionResult.SUCCESS
            }

            return InteractionResult.FAIL
        }
    }

    private fun updateShape(connectionsInfo: List<Int>) {
        val isFilled = checkIsFilledVariant(connectionsInfo)

        var shape = if(isFilled) {
            Shapes.empty()
        } else {
            partProviderShape
        }

        val shapeSet = if(isFilled) {
            connectionBoundsFilled
        } else {
            connectionBounds
        }

        for (connectionInfo in connectionsInfo) {
            val connection = shapeSet[getConnectionShapeKey(connectionInfo)]
                ?: continue

            shape = Shapes.joinUnoptimized(shape, connection, BooleanOp.OR)
        }

        updateShape(shape)
    }

    @ServerOnly
    private fun updateShapeServer() {
        check(!placement.level.isClientSide)
        updateShape(getConnectionsInfo())
    }

    @ClientOnly
    private fun updateShapeClient(data: IntArray) {
        check(placement.level.isClientSide)
        updateShape(ImmutableIntArrayView(data))
    }

    @ClientOnly
    override fun createRenderer(): WireRenderer<*> {
        val supplier = renderInfo ?: error("Render info is null")
        val renderInfo = supplier.get()

        return if(isIncandescent) {
            if(Backend.getBackendType() == BackendType.INSTANCING) {
                // Instancing is supported, so use good renderer:
                IncandescentInstancedWireRenderer(this, renderInfo)
            } else {
                // Maybe make batched fallback with temperature, not worth investing time into because batched will probably be removed
                FlatWireRenderer(this, renderInfo)
            }
        } else {
            FlatWireRenderer(this, renderInfo)
        }
    }

    override fun onPlaced() {
        super.onPlaced()

        if (!placement.level.isClientSide) {
            setSyncDirty()
        }
    }

    /**
     * Called when sending the connections to the client.
     * */
    @ServerOnly
    override fun getSyncTag(): CompoundTag {
        return CompoundTag().also { tag ->
            val directionList = ListTag()

            for (it in cell.connections) {
                val directSolution = getPartConnectionAsContactSectionConnectionOrNull(cell, it)
                    ?: continue

                directionList.add(directSolution.toNbt())
            }

            tag.put(DIRECTIONS, directionList)
        }
    }

    /**
     * Called when the client is loading the connections.
     * */
    @ClientOnly
    override fun handleSyncTag(tag: CompoundTag) {
        if (tag.contains(DIRECTIONS)) {
            val directionList = tag.get(DIRECTIONS) as ListTag
            val data = IntArray(directionList.size)

            directionList.forEachIndexed { i, t ->
                data[i] = PartConnectionRenderInfo.fromNbt(t as CompoundTag).value
            }

            renderer.acceptConnections(data)
            updateShapeClient(data)
        }
    }

    @ServerOnly
    override fun getInitialSyncTag() = getSyncTag()

    @ClientOnly
    override fun loadInitialSyncTag(tag: CompoundTag) = handleSyncTag(tag)

    @ServerOnly
    override fun onConnectivityChanged() {
        setSyncDirty()
        updateShapeServer()
    }

    @ClientOnly
    override fun registerPackets(builder: PacketHandlerBuilder) {
        builder.withHandler<InternalTemperaturePacket> {
            if(isIncandescent) {
                (this.renderer as? InternalTemperatureConsumerWire)?.updateInternalTemperature(it.temperature)
            }

            if(it.temperature >= smokeTemperature) {
                placement.multipart.addAnimated(this)
            }
            else {
                placement.multipart.markRemoveAnimated(this)
            }
        }

        if(isIncandescent) {
            // The solution to prevent unhandled packets would be to send some render capabilities info
            // from client to server. Maybe in the future
            builder.withHandler<ExternalTemperaturesPacket> {
                (this.renderer as? ExternalTemperatureConsumerWire)?.updateExternalTemperatures(it.temperatures)
            }
        }
    }

    @ServerOnly
    override fun onInternalTemperatureChanges(dirty: List<ThermalBody>) {
        sendBulkPacket(InternalTemperaturePacket(dirty.first().temperatureKelvin))
    }

    /**
     * Receives the temperatures of neighbor cells, from the simulation thread.
     * The cell neighbor cache is updated with newly discovered thermal objects, the updates are organised and then sent to the client with [ExternalTemperaturesPacket]
     * */
    @ServerOnly @OnSimulationThread
    override fun onExternalTemperatureChanges(removed: HashSet<ThermalObject<*>>, dirty: HashMap<ThermalObject<*>, Double>) {
        val temperatures = Int2DoubleOpenHashMap()

        for ((thermalObject, temperature) in dirty) {
            val solution = getPartConnectionAsContactSectionConnectionOrNull(cell, thermalObject.cell)
                ?: continue

            temperatures.put(solution.value, temperature)
        }

        sendBulkPacket(ExternalTemperaturesPacket(temperatures))
    }

    /**
     * Sends the latest internal temperature (from reading the cell's temperature field) and the latest neighbor temperatures (from [cellNeighborCache])
     * */
    @ServerOnly
    override fun onSyncSuggested() {
        if(isIncandescent) {
            if(hasCell) {
                val cell = this.cell

                if(cell is ThermalWireCell) {
                    sendBulkPacket(InternalTemperaturePacket(cell.thermalWire.readTemperature()))
                }

                val externalTemperatures = Int2DoubleOpenHashMap()

                ExternalTemperatureReplicatorBehavior.scanNeighbors(cell) { remoteThermalObject, temperature ->
                    val solution = getPartConnectionOrNull(this.cell.locator, remoteThermalObject.cell.locator)
                        ?: return@scanNeighbors

                    externalTemperatures.put(solution.value, temperature)
                }

                if(externalTemperatures.isNotEmpty()) {
                    sendBulkPacket(ExternalTemperaturesPacket(externalTemperatures))
                }
            }
        }
    }

    override fun onCellAcquired() {
        updateShapeServer()
    }

    @Serializable
    private data class InternalTemperaturePacket(val temperature: Double)

    @Serializable
    private data class ExternalTemperaturesPacket(val temperatures: Map<Int, Double>)

    override fun animationTick(random: RandomSource) {
        repeat(5) {
            placement.level.addParticle(
                ParticleTypes.SMOKE,
                placement.position.x + 0.5 + random.nextDouble(-0.25, 0.25),
                placement.position.y + random.nextDouble(0.05, 0.15),
                placement.position.z + 0.5 + random.nextDouble(-0.25, 0.25),
                random.nextDouble(-0.01, 0.01),
                random.nextDouble(0.01, 0.1),
                random.nextDouble(-0.01, 0.01)
            )
        }
    }

    override fun appendWaila(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        runWithCell {
            if(it is ThermalWireCell) {
                builder.temperature(it.thermalWire.readTemperature())
            }
        }
    }
}

/**
 * Trait for a wire renderer that displays the **Internal Temperature** (temperature of the cell being rendered)
 * */
interface InternalTemperatureConsumerWire {
    /**
     * Called when the core temperature has changed.
     * */
    fun updateInternalTemperature(temperature: Double)
}

/**
 * Trait for a wire renderer that displays the **External Temperatures** (temperatures at the junction with other cells)
 * */
interface ExternalTemperatureConsumerWire {
    /**
     * Called when junction temperatures have changed.
     * */
    fun updateExternalTemperatures(temperatures: Map<Int, Double>)
}

@JvmInline
value class PartConnectionRenderInfo(val value: Int) {
    val mode get() = CellPartConnectionMode.byId[(value and 3)]
    val directionPart get() = Base6Direction3d.byId[(value shr 2) and 7]
    val flag get() = (value and 32) != 0

    constructor(mode: CellPartConnectionMode, directionPart: Base6Direction3d, flag: Boolean) : this(mode.index or (directionPart.id shl 2) or (if(flag) 32 else 0))

    val partConnectionDirection get() = PartConnectionDirection(mode, directionPart)

    fun toNbt(): CompoundTag {
        val tag = CompoundTag()

        tag.putBase6Direction(DIR, directionPart)
        tag.putConnectionMode(MODE, mode)
        tag.putBoolean(FLAG, flag)

        return tag
    }

    companion object {
        private const val MODE = "mode"
        private const val DIR = "dir"
        private const val FLAG = "flag"

        fun cast(from: PartConnectionDirection) = PartConnectionRenderInfo(from.mode, from.directionPart, false)

        fun fromNbt(tag: CompoundTag) = PartConnectionRenderInfo(
            tag.getConnectionMode(MODE),
            tag.getDirectionActual(DIR),
            tag.getBoolean(FLAG)
        )
    }
}

interface CellContactPointSurface {
    fun getContactSection(cell: Cell) : Double
}
// to improve
fun getPartConnectionAsContactSectionConnectionOrNull(cell: Cell, remoteCell: Cell) : PartConnectionRenderInfo? {
    val partSolution = getPartConnectionOrNull(cell.locator, remoteCell.locator)
        ?: return null

    if(cell !is CellContactPointSurface || remoteCell !is CellContactPointSurface) {
        return PartConnectionRenderInfo.cast(partSolution)
    }

    val flag = cell.getContactSection(remoteCell) < remoteCell.getContactSection(cell)

    return PartConnectionRenderInfo(
        partSolution.mode,
        partSolution.directionPart,
        flag
    )
}

fun interface PartConnectionRenderInfoSetConsumer {
    fun acceptConnections(connections: IntArray)
}

private fun checkIsFilledVariant(connections: IntArray) = if (connections.size == 2) {
    val c1 = PartConnectionDirection(connections[0])
    val c2 = PartConnectionDirection(connections[1])
    c1.directionPart == c2.directionPart.opposite
} else false

private fun checkIsFilledVariant(connections: List<Int>) = if (connections.size == 2) {
    val c1 = PartConnectionDirection(connections[0])
    val c2 = PartConnectionDirection(connections[1])
    c1.directionPart == c2.directionPart.opposite
} else false

abstract class WireRenderer<I>(
    val part: WirePart<*>,
    val renderInfo: WireRenderInfo,
) : PartRenderer(), PartConnectionRenderInfoSetConsumer {
    private var connectionsUpdate = AtomicUpdate<IntArray>()
    protected var hubInstance: ModelData? = null
    protected var connectionInstances = Int2ObjectOpenHashMap<I>(4)

    fun forEachConnectionInfo(user: (PartConnectionDirection) -> Unit) {
        if(connectionInstances.isEmpty()) {
            return
        }

        val iterator = connectionInstances.keys.intIterator()
        while (iterator.hasNext()) {
            user(PartConnectionDirection(iterator.nextInt()))
        }
    }

    protected fun putUniqueConnection(key: Int, instance: I) {
        require(connectionInstances.put(key, instance) == null) {
            "Duplicate $this wire renderer direction"
        }
    }

    protected fun<T : Transform<T>> T.poseHub(): T =
        this.transformPart(
            part,
            0.0
        )

    protected fun<T : Transform<T>> T.poseConnection(info: PartConnectionDirection): T =
        this.transformPart(
            part,
            when (info.directionPart) {
                Base6Direction3d.Front -> 0.0
                Base6Direction3d.Back -> PI
                Base6Direction3d.Left -> PI / 2.0
                Base6Direction3d.Right -> -PI / 2.0
                else -> error("Invalid wire direction ${info.directionPart}")
            }
        )

    override fun acceptConnections(connections: IntArray) {
        connectionsUpdate.setLatest(connections)
    }

    // We don't do any initialization here, but we re-create the models because it gets called when the pipeline changes.s
    override fun setupRendering() {
        // Re-load models:
        applyConnectionData(connectionInstances.keys.toIntArray())
    }

    override fun beginFrame() {
        connectionsUpdate.consume { directions ->
            applyConnectionData(directions)
        }
    }

    /**
     * Applies the received directions by creating a hub instance and connection instances.
     * @param partConnections An array of [PartConnectionDirection]. If empty, no connection instances will be created.
     * */
    protected abstract fun applyConnectionData(partConnections: IntArray)

    override fun relight(source: RelightSource) {
        multipart.relightModels(hubInstance)

        for (instance in connectionInstances.values) {
            relightConnection(instance, source)
        }
    }

    protected abstract fun relightConnection(instance: I, source: RelightSource)

    override fun remove() {
        deleteInstances()
    }

    protected open fun deleteInstances() {
        hubInstance?.delete()
        hubInstance = null

        for (instance in connectionInstances.values) {
            deleteConnection(instance)
        }

        connectionInstances.clear()
    }

    protected abstract fun deleteConnection(instance: I)
}

/**
 * Wire renderer without any temperature visualization.
 * To be used for insulated wires or as fallback.
 * */
class FlatWireRenderer(
    part: WirePart<*>,
    renderInfo: WireRenderInfo,
) : WireRenderer<ModelData>(part, renderInfo) {
    private fun createHubInstance(): ModelData =
        multipart.materialManager
            .defaultSolid()
            .material(Materials.TRANSFORMED)
            .getModel(renderInfo.hub)
            .createInstance()
            .loadIdentity()
            .poseHub()

    private fun createConnectionInstance(info: PartConnectionDirection, model: PolarModel) =
        multipart.materialManager
            .defaultSolid()
            .material(Materials.TRANSFORMED)
            .getModel(model)
            .createInstance()
            .loadIdentity()
            .poseConnection(info)

    override fun applyConnectionData(partConnections: IntArray) {
        // We can afford to be wasteful and always re-create everything because this happens only when wires
        // are placed and removed.
        deleteInstances()

        val isFilledVariant = checkIsFilledVariant(partConnections)

        if (!isFilledVariant) {
            // If not filled, it means we need a hub (junction):
            hubInstance = createHubInstance()
        }

        val variants = renderInfo.connection.variants[isFilledVariant]!!

        for (connection in partConnections) {
            val info = PartConnectionDirection(connection)
            val connectionInstance = createConnectionInstance(info, variants[info.mode]!!)
            putUniqueConnection(info.value, connectionInstance)
        }

        // We re-created the models, so we need to re-upload lights:
        relight(RelightSource.Setup)
    }

    override fun relightConnection(instance: ModelData, source: RelightSource) {
        multipart.relightModels(instance)
    }

    override fun deleteConnection(instance: ModelData) {
        instance.delete()
    }
}

/**
 * Wire renderer that implements [InternalTemperatureConsumerWire] and [ExternalTemperatureConsumerWire]. It depends on instanced rendering.
 * Terminology:
 *  - Core (Internal) Temperature - the temperature of the game object this renderer is bound to.
 *  - External Temperatures - the temperatures of the game objects adjacent to the wire this renderer is bound to.
 *
 * Technique:
 *  - The hub is tinted using our core temperature
 *  - Connections are implemented as [PolarModel]s
 *  1. The poles visually adjacent to our hub are tinted with the color of our hub
 *  2. The pole of a connection that is adjacent to another thermal object is tinted using a geometric rule:
 *      - The desired look is basically like 1 big connection that joins the two hubs
 *      - The poles adjacent to the two hubs are tinted with the color of the respective hubs (1)
 *      - [PolarData] uses linear fragment interpolation, so the interpolated color at the "middle" of the imaginary big connection can be applied by setting this color at the adjacent external poles of the two actual connections.
 *          This rule doesn't behave exactly as expected if the remote game object is not a wire, but it is fine for now.
 *
 * */
class IncandescentInstancedWireRenderer(
    part: WirePart<*>,
    renderInfo: WireRenderInfo,
) : WireRenderer<PolarData>(part, renderInfo), InternalTemperatureConsumerWire, ExternalTemperatureConsumerWire {
    private val internalTemperatureUpdates = AtomicUpdate<Double>()
    private val externalTemperatureUpdates = AtomicUpdate<Map<Int, Double>>()
    private var internalTemperature = 0.0
    private val externalTemperatures = Int2DoubleOpenHashMap()

    override fun relightConnection(instance: PolarData, source: RelightSource) {
        instance.setSkyLight(multipart.readSkyBrightness())
        uploadCoreData()
        uploadRemoteData(externalTemperatures)
    }

    override fun deleteConnection(instance: PolarData) {
        instance.delete()
    }

    /**
     * Evaluates the color as R, G, B and light override.
     * @param temperature The temperature of the material.
     * @param light The lower bound of the light value [[0, 15]]
     * @return The tint color to be rendered.
     * */
    private fun evaluateRGBL(temperature: Double, light: Double = 0.0): Color {
        val rgb = renderInfo.tintColor.evaluate(temperature)

        return Color(
            rgb.red,
            rgb.green,
            rgb.blue,
            max(
                map(
                    light,
                    0.0,
                    15.0,
                    0.0,
                    255.0
                ),
                map(
                    temperature,
                    renderInfo.tintColor.coldTemperature.kelvin,
                    renderInfo.tintColor.hotTemperature.kelvin,
                    0.0,
                    255.0
                )
            ).toInt().coerceIn(0, 255)
        )
    }

    private fun createHubInstance(): ModelData =
        multipart.materialManager
            .defaultSolid()
            .material(ModelLightOverrideType)
            .getModel(renderInfo.hub)
            .createInstance()
            .loadIdentity()
            .poseHub()
            .also {
                it.setColor(colorF(1.0f, 1.0f, 1.0f, 0.0f))
            }

    private fun createConnectionInstance(info: PartConnectionDirection, model: PolarModel) =
        multipart.materialManager
            .defaultSolid()
            .material(PolarType)
            .getModel(model)
            .createInstance()
            .loadIdentity()
            .poseConnection(info)
            .also {
                it.setColor1(colorF(1.0f, 1.0f, 1.0f, 0.0f))
                it.setColor2(colorF(1.0f, 1.0f, 1.0f, 0.0f))
            }

    /**
     * Enqueues an update for the core temperature (the actual temperature of this thermal object)
     * */
    override fun updateInternalTemperature(temperature: Double) {
        internalTemperatureUpdates.setLatest(temperature)
    }

    /**
     * Enqueues an update for the external temperatures (the temperatures of neighbor thermal objects)
     * */
    override fun updateExternalTemperatures(temperatures: Map<Int, Double>) {
        externalTemperatureUpdates.setLatest(temperatures)
    }

    override fun applyConnectionData(partConnections: IntArray) {
        deleteInstances()

        val isFilledVariant = checkIsFilledVariant(partConnections)

        if (!isFilledVariant) {
            // If not filled, it means we need a hub (junction):
            hubInstance = createHubInstance()
        }

        val models = renderInfo.connection.variants[isFilledVariant]!!
        val removedNeighbors = IntOpenHashSet(externalTemperatures.keys)

        for (connection in partConnections) {
            val info = PartConnectionDirection(connection)

            putUniqueConnection(
                info.value,
                createConnectionInstance(info, models[info.mode]!!)
            )

            // Mark the connection as still existing:
            removedNeighbors.remove(info.value)
        }

        // Remove cached temperatures of objects that are not connected.
        // Doing this instead of clearing might prevent some micro-flickers.
        if(removedNeighbors.isNotEmpty()) {
            val iterator = removedNeighbors.intIterator()
            while(iterator.hasNext()) {
                externalTemperatures.remove(iterator.nextInt())
            }
        }

        // We re-created the models, so we need to re-upload lights:
        relight(RelightSource.Setup)
        // Re-upload saved core temperature:
        uploadCoreData()
        // Re-upload saved external temperatures
        uploadRemoteData(externalTemperatures)
    }

    override fun beginFrame() {
        super.beginFrame()

        internalTemperatureUpdates.consume { internalTemperature ->
            this.internalTemperature = internalTemperature
            uploadCoreData()
        }

        externalTemperatureUpdates.consume { updates ->
            this.externalTemperatures.putAll(updates)
            uploadRemoteData(updates)
        }
    }

    private fun setExteriorPoleColor(instance: PolarData, core: Color, pole: Color) {
        instance.setColor1(colorLerp(core, pole, 0.5f))
    }

    private fun setExteriorPoleColor(instance: PolarData, coreColor: Color, remoteInfo: Int, remoteTemperature: Double) {
        setExteriorPoleColor(instance, coreColor, evaluateRemoteColor(remoteInfo, remoteTemperature))
    }

    /**
     * Evaluates the color of the hub and hub-connection junctions.
     * */
    private fun evaluateCoreColor() : Color {
        val coreLightLevel = multipart.readBlockBrightness().toDouble()

        return evaluateRGBL(internalTemperature, coreLightLevel)
    }

    /**
     * Evaluates the color at the junction between this wire and the remote wire at [remoteInfo], with the [remoteTemperature].
     * */
    private fun evaluateRemoteColor(remoteInfo: Int, remoteTemperature: Double) : Color {
        val remotePositionWorld = part.placement.position + PartConnectionDirection(remoteInfo).getIncrement(
            part.placement.horizontalFacing,
            part.placement.face
        )

        val remoteLightLevel = multipart.world.getBrightness(
            LightLayer.BLOCK,
            remotePositionWorld
        )

        return evaluateRGBL(remoteTemperature, remoteLightLevel.toDouble())
    }

    /**
     * Updates the core color (the tint of the hub), based on the [internalTemperature].
     * Also adjusts the core contact colors for all [connectionInstances]
     * */
    private fun uploadCoreData() {
        val coreColor = evaluateCoreColor()

        hubInstance?.setColor(coreColor)

        for ((remoteInfo, instance) in connectionInstances) {
            instance.setColor2(coreColor)

            // Since the pole color depends on core color, we need to update that as well:
            setExteriorPoleColor(instance, coreColor, remoteInfo, externalTemperatures.get(remoteInfo))
        }
    }

    /**
     * Updates the remote/external colors (the tint of the outer poles of the [connectionInstances]),
     * for the instances corresponding to entries in [temperatures].
     * */
    private fun uploadRemoteData(temperatures: Map<Int, Double>) {
        val coreColor = evaluateCoreColor()

        for ((remoteInfo, remoteTemperature) in temperatures) {
            val instances = connectionInstances.get(remoteInfo)
                ?: continue

            setExteriorPoleColor(instances, coreColor, remoteInfo, remoteTemperature)
        }
    }
}
