@file:Suppress("NonAsciiCharacters")

package org.eln2.mc.common.content

import com.jozufozu.flywheel.backend.Backend
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
import net.minecraft.world.level.LightLayer
import net.minecraft.world.phys.Vec3
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
import org.eln2.mc.integration.WailaEntity
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
class ThermalWireObject(cell: Cell, thermalDefinition: ThermalBodyDef) : ThermalObject<Cell>(cell), WailaEntity, DataContainer, PersistentObject {
    companion object {
        // Storing temperature. If I change the properties of the material, it will be the same temperature in game.
        private const val TEMPERATURE = "temperature"
    }

    override val dataNode = data {
        it.withField(TemperatureField {
            thermalBody.temperature
        })

        it.withField(EnergyField {
            thermalBody.energy
        })

        it.withField(MassField {
            thermalBody.thermal.mass
        })
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
}

/**
 * Generalized electrical wire, created by joining the "internal" pins of a [ResistorBundle].
 * The "external" pins are offered to other cells.
 * */
class ElectricalWireObject(cell: Cell) : ElectricalObject<Cell>(cell), WailaEntity, DataContainer {
    override val dataNode = data {
        it.withField(ResistanceField {
            resistance
        })

        it.withField(PowerField {
            resistors.totalPower
        })
    }

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
 * @param hubHeight The real height of the hub model (for BlockBench, it is **size along Y / 16.0**)
 * @param connection The connection models
 * @param connectionHeight The real height of the connection model (for BlockBench, it is **size along Y / 16.0**)
 * */
data class WireRenderInfo(
    val hub: PartialModel,
    val hubHeight: Double,
    val connection: WireConnectionModel,
    val connectionHeight: Double,
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

abstract class WireBuilder<C : Cell>(val id: String) {
    var material = ThermalBodyDef(Material.COPPER, 1.0, cylinderSurfaceArea(1.0, 0.05))
    var damageOptions = TemperatureExplosionBehaviorOptions()
    var replicatesInternalTemperature = true
    var isIncandescent: Boolean = true
    var size = Vec3(0.1, 0.1, 0.1)
    var tint = defaultRadiantBodyColor()
    var smokeTemperature: Double? = null
    private var renderInfo: Supplier<WireRenderInfo>? = null

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
        PartRegistry.part(
            id,
            BasicPartProvider({ ix, ctx ->
                WirePart(
                    id = ix,
                    context = ctx,
                    cellProvider = provider.get(),
                    isIncandescent,
                    smokeTemperature ?: (!properties.damageOptions.temperatureThreshold * 0.9),
                    renderInfo = renderInfo ?: Supplier {
                        defaultRender()
                    }
                )
            }, size)
        )
    }
}

class ThermalWireBuilder(id: String) : WireBuilder<ThermalWireCell>(id) {
    fun register(): ThermalWireRegistryObject {
        val material = createMaterialProperties()
        val cell = CellRegistry.injCell<ThermalWireCell>(id, material)
        registerPart(material, cell)
        return ThermalWireRegistryObject(material, cell.id)
    }

    override fun defaultRender() = WireRenderInfo(
        PartialModels.THERMAL_WIRE_HUB,
        1.5 / 16.0,
        PartialModels.THERMAL_WIRE_CONNECTION,
        1.5 / 16.0,
        tint
    )
}

class ElectricalWireBuilder(id: String) : WireBuilder<ElectricalWireCell>(id) {
    var resistance: Double = 2.14 * 1e-5

    fun register(): ElectricalWireRegistryObject {
        val material = createMaterialProperties()
        val electrical = WireElectricalProperties(resistance)
        val cell = CellRegistry.injCell<ElectricalWireCell>(id, material, electrical)
        registerPart(material, cell)
        return ElectricalWireRegistryObject(material, electrical, cell.id)
    }

    override fun defaultRender() = WireRenderInfo(
        PartialModels.ELECTRICAL_WIRE_HUB,
        1.5 / 16.0,
        PartialModels.ELECTRICAL_WIRE_CONNECTION,
        1.5 / 16.0,
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
    val replicatesExternalTemperature: Boolean,
)

/**
 * Electrical properties of a wire.
 * @param electricalResistance The electrical resistance.
 * */
data class WireElectricalProperties(
    val electricalResistance: Double,
)

open class ThermalWireCell(ci: CellCreateInfo, val thermalProperties: WireThermalProperties) : Cell(ci) {
    @SimObject
    val thermalWire = ThermalWireObject(this)

    @Behavior
    val explosion = TemperatureExplosionBehavior(
        thermalWire::readTemperature,
        thermalProperties.damageOptions,
        this
    )

    init {
        dataNode.pull<TemperatureField>(thermalWire)
    }

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

open class ElectricalWireCell(ci: CellCreateInfo, thermalProperties: WireThermalProperties, val electricalProperties: WireElectricalProperties) : ThermalWireCell(ci, thermalProperties) {
    @SimObject
    val electricalWire = ElectricalWireObject(this).also {
        it.resistance = electricalProperties.electricalResistance
    }

    @Behavior
    val heater = PowerHeatingBehavior(
        electricalWire::totalPower,
        thermalWire.thermalBody
    )

    init {
        dataNode.pull<ResistanceField>(electricalWire)
        dataNode.pull<PowerField>(electricalWire)
    }

    override fun appendWaila(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        builder.text("Connections", connections.size)

        super.appendWaila(builder, config)
    }
}

class WirePart<C : Cell>(
    id: ResourceLocation,
    context: PartPlacementInfo,
    cellProvider: CellProvider<C>,
    val isIncandescent: Boolean,
    val smokeTemperature: Double,
    val renderInfo: Supplier<WireRenderInfo>?,
) : CellPart<C, WireRenderer<*>>(id, context, cellProvider), InternalTemperatureConsumer, ExternalTemperatureConsumer, AnimatedPart {
    companion object {
        private const val DIRECTIONS = "directions"
    }

    override val partSize = bbVec(8.0, 2.0, 8.0)

    @ClientOnly
    override fun createRenderer(): WireRenderer<*> {
        val supplier = renderInfo ?: error("Render info is null")
        val renderInfo = supplier.get()

        return if(isIncandescent) {
            if(Backend.canUseInstancing(placement.level)) {
                // Instancing is supported, so use good renderer:
                IncandescentInstancedWireRenderer(this, renderInfo)
            } else {
                // Maybe make batched fallback, not worth investing time into
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
                val solution = getPartConnectionOrNull(cell.locator, it.locator)
                    ?: continue

                directionList.add(solution.toNbt())
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
                data[i] = PartConnectionDirection.fromNbt(t as CompoundTag).data
            }

            renderer.updateDirections(data)
        }
    }

    @ServerOnly
    override fun getInitialSyncTag() = getSyncTag()

    @ClientOnly
    override fun loadInitialSyncTag(tag: CompoundTag) = handleSyncTag(tag)

    @ServerOnly
    override fun onConnected(remoteCell: Cell) {
        setSyncDirty()
    }

    @ServerOnly
    override fun onDisconnected(remoteCell: Cell) {
        if(!cell.isBeingRemoved) {
            // Don't send updates if we're being removed
            setSyncDirty()
            return
        }
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
            val solution = getPartConnectionOrNull(cell.locator, thermalObject.cell.locator)
                ?: continue

            temperatures.put(solution.data, temperature)
        }

        sendBulkPacket(ExternalTemperaturesPacket(temperatures))
    }

    /**
     * Sends the latest internal temperature (from reading the cell's temperature field) and the latest neighbor temperatures (from [cellNeighborCache])
     * */
    @ServerOnly
    override fun onSyncSuggested() {
        if(isIncandescent) {
            cell.data.getOrNull<TemperatureField>()?.also {
                sendBulkPacket(InternalTemperaturePacket(it.readKelvin()))
            }

            val externalTemperatures = Int2DoubleOpenHashMap()

            ExternalTemperatureReplicatorBehavior.scanNeighbors(cell) { remoteThermalObject, field ->
                val solution = getPartConnectionOrNull(this.cell.locator, remoteThermalObject.cell.locator)
                    ?: return@scanNeighbors

                externalTemperatures.put(solution.data, field.readKelvin())
            }

           if(externalTemperatures.isNotEmpty()) {
               sendBulkPacket(ExternalTemperaturesPacket(externalTemperatures))
           }
        }
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

abstract class WireRenderer<I>(
    val part: WirePart<*>,
    val renderInfo: WireRenderInfo,
) : PartRenderer() {
    private var connectionsUpdate = AtomicUpdate<IntArray>()
    protected var hubInstance: ModelData? = null
    protected var connectionInstances = Int2ObjectOpenHashMap<I>(4)

    protected fun putUniqueConnection(key: Int, instance: I) {
        require(connectionInstances.put(key, instance) == null) {
            "Duplicate $this wire renderer direction"
        }
    }

    protected fun<T : Transform<T>> T.poseHub(): T =
        this.translateNormal(part.placement.face, -renderInfo.hubHeight * (2f / 3f))
        .translate(0.5, 0.0, 0.5)
        .translate(part.worldBoundingBox.center)
        .multiply(
            part.placement.face.rotation.toJoml()
                .mul(part.facingRotation)
            .toMinecraft()
        )
        .translate(-0.5, 0.0, -0.5)

    protected fun<T : Transform<T>> T.poseConnection(info: PartConnectionDirection): T =
        this.translateNormal(part.placement.face, -renderInfo.connectionHeight * (2f / 3f))
        .translate(0.5, 0.0, 0.5)
        .translate(part.worldBoundingBox.center)
        .multiply(
            part.placement.face.rotation.toJoml()
                .mul(part.facingRotation)
                .rotateY(
                    when (info.directionPart) {
                        Base6Direction3d.Front -> 0.0
                        Base6Direction3d.Back -> PI
                        Base6Direction3d.Left -> PI / 2.0
                        Base6Direction3d.Right -> -PI / 2.0
                        else -> error("Invalid wire direction ${info.directionPart}")
                    }.toFloat()
                )
            .toMinecraft()
        )
        .translate(-0.5, 0.0, -0.5)

    fun updateDirections(partConnections: IntArray) = connectionsUpdate.setLatest(partConnections)

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

    companion object {
        fun checkIsFilledVariant(connections: IntArray) = if (connections.size == 2) {
            // Check for full connection:

            val c1 = PartConnectionDirection(connections[0])
            val c2 = PartConnectionDirection(connections[1])

            c1.directionPart == c2.directionPart.opposite
        } else false
    }
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
            putUniqueConnection(info.data, connectionInstance)
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
                info.data,
                createConnectionInstance(info, models[info.mode]!!)
            )

            // Mark the connection as still existing:
            removedNeighbors.remove(info.data)
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
