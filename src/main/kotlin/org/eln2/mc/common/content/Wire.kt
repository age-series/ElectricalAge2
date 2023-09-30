@file:Suppress("NonAsciiCharacters")

package org.eln2.mc.common.content

import com.jozufozu.flywheel.core.Materials
import com.jozufozu.flywheel.core.PartialModel
import com.jozufozu.flywheel.core.materials.FlatLit
import com.jozufozu.flywheel.core.materials.model.ModelData
import com.jozufozu.flywheel.util.Color
import kotlinx.serialization.Serializable
import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.fml.DistExecutor
import net.minecraftforge.registries.RegistryObject
import org.ageseries.libage.sim.Material
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.thermal.*
import org.eln2.mc.*
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.foundation.MultipartBlockEntityInstance
import org.eln2.mc.client.render.foundation.RadiantBodyColor
import org.eln2.mc.client.render.foundation.defaultRadiantBodyColor
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.events.AtomicUpdate
import org.eln2.mc.common.events.schedulePre
import org.eln2.mc.common.network.serverToClient.PacketHandlerBuilder
import org.eln2.mc.common.parts.PartRegistry
import org.eln2.mc.common.parts.foundation.*
import org.eln2.mc.data.*
import org.eln2.mc.integration.WailaEntity
import org.eln2.mc.integration.WailaTooltipBuilder
import org.eln2.mc.mathematics.*
import org.joml.AxisAngle4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.function.Supplier
import kotlin.math.PI

/**
 * Represents a thermal wire, in the form of a thermal body. This body gets connected to all neighbor cells.
 * */
class ThermalWireObject(cell: Cell, thermalDefinition: ThermalBodyDef) : ThermalObject(cell), WailaEntity, DataEntity, PersistentObject {
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
            cell.environmentData.getOrNull<EnvironmentalTemperatureField>()?.readTemperature()?.also {
                body.temperature = it
            }
        }
    }

    override fun offerComponent(neighbour: ThermalObject) = ThermalComponentInfo(thermalBody)

    override fun addComponents(simulator: Simulator) {
        simulator.add(thermalBody)

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
 * Represents an electrical wire, created by joining "internal" pins of a [ResistorBundle]. The "external" pins are exported to other cells.
 * */
class ElectricalWireObject(cell: Cell) : ElectricalObject(cell), WailaEntity, DataEntity {
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

    override fun offerComponent(neighbour: ElectricalObject) = resistors.getOfferedResistor(neighbour)

    override fun clearComponents() = resistors.clear()

    override fun addComponents(circuit: Circuit) = resistors.register(connections, circuit)

    override fun build() {
        resistors.connect(connections, this)

        // Is there a better way to do this?

        // The Wire uses a bundle of 4 resistors. Every resistor's "Internal Pin" is connected to every
        // other resistor's internal pin. "External Pins" are offered to connection candidates.

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
 * Holds information needed to render a wire.
 * @param meshes The 3D models for every possible wire configuration.
 * */
data class WireRenderInfo(
    val meshes: Map<Base6Direction3dMask, PartialModel>,
    val color: RadiantBodyColor
)

/**
 * Holds information regarding a registered thermal wire.
 * @param thermalProperties The strictly thermal properties of the wire.
 * @param id The ID of the wire cell.
 * */
data class ThermalWireRegistryObject(
    val thermalProperties: WireThermalProperties,
    val id: ResourceLocation
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
    val id: ResourceLocation
)

abstract class WireBuilder<C : Cell>(val id: String) {
    var material = ThermalBodyDef(Material.COPPER, 1.0, cylinderSurfaceArea(1.0, 0.1))

    var damageOptions = TemperatureExplosionBehaviorOptions()
    var radiatesLight: Boolean = true
    var size = Vec3(0.1, 0.1, 0.1)

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

    protected fun createMaterialProperties() = WireThermalProperties(material, damageOptions, radiatesLight)

    protected fun registerPart(properties: WireThermalProperties, provider: RegistryObject<CellProvider<C>>) {
        PartRegistry.part(
            id,
            BasicPartProvider({ ix, ctx ->
                WirePart(
                    id = ix,
                    context = ctx,
                    cellProvider = provider.get(),
                    radiatesLight,
                    renderInfo = renderInfo ?: Supplier {
                        defaultRender()
                    }
                )
            }, size))
    }
}

class ThermalWireBuilder(id: String) : WireBuilder<ThermalWireCell>(id) {
    fun register(): ThermalWireRegistryObject {
        val material = createMaterialProperties()
        val cell = CellRegistry.injCell<ThermalWireCell>(id, material)
        registerPart(material, cell)
        return ThermalWireRegistryObject(material, cell.id)
    }

    override fun defaultRender() = WireRenderInfo(WireMeshSets.thermalWireMap, defaultRadiantBodyColor())
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

    override fun defaultRender() = WireRenderInfo(WireMeshSets.electricalWireMap, defaultRadiantBodyColor())
}

/**
 * Thermal properties of a wire.
 * @param def The definition used to create the thermal body of the wire.
 * @param damageOptions The damage config, paseed to the [TemperatureExplosionBehavior]
 * @param radiatesLight Indicates whether the wire glows (is incandescent) when heated to high temperatures.
 * */
data class WireThermalProperties(
    val def: ThermalBodyDef,
    val damageOptions: TemperatureExplosionBehaviorOptions,
    val radiatesLight: Boolean
)

/**
 * Electrical properties of a wire.
 * @param electricalResistance The electrical resistance.
 * */
data class WireElectricalProperties(
    val electricalResistance: Double
)

open class ThermalWireCell(ci: CellCreateInfo, val thermalProperties: WireThermalProperties) : Cell(ci) {
    @SimObject @Inspect
    val thermalWire = ThermalWireObject(this)

    @Behavior
    val explosion = TemperatureExplosionBehavior(
        {thermalWire.thermalBody.temperature.kelvin },
        thermalProperties.damageOptions,
        this
    )

    @Replicator
    fun temperatureReplicator(thermalReplicator: TemperatureReplicator) =
        if (thermalProperties.radiatesLight)
            ThermalReplicatorBehavior(listOf(thermalWire.thermalBody), thermalReplicator)
        else null
}

class ElectricalWireCell(ci: CellCreateInfo, thermalProperties: WireThermalProperties, val electricalProperties: WireElectricalProperties) : ThermalWireCell(ci, thermalProperties) {
    @SimObject @Inspect
    val electricalWire = ElectricalWireObject(this).also {
        it.resistance = electricalProperties.electricalResistance
    }

    @Behavior
    val heater = PowerHeatingBehavior(
        { electricalWire.totalPower },
        thermalWire.thermalBody
    )

    override fun appendWaila(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        builder.text("Connections", connections.size)

        super.appendWaila(builder, config)
    }
}

class WirePart<C : Cell>(
    id: ResourceLocation,
    context: PartPlacementInfo,
    cellProvider: CellProvider<C>,
    val radiatesGameLight: Boolean,
    val renderInfo: Supplier<WireRenderInfo>?
) : CellPart<C, WirePartRenderer>(id, context, cellProvider), TemperatureReplicator {
    companion object {
        private const val DIRECTIONS = "directions"
        private const val COLD_LIGHT_LEVEL = 0.0
        private const val COLD_LIGHT_TEMPERATURE = 500.0
        private const val HOT_LIGHT_LEVEL = 5.0
        private const val HOT_LIGHT_TEMPERATURE = 1000.0
    }

    override val partSize = bbVec(8.0, 2.0, 8.0)

    private data class Connection(val info: CellPartConnectionInfo, val locator: LocatorSet) {
        fun toNbt(): CompoundTag {
            val tag = CompoundTag()

            tag.putBase6Direction(DIR, info.actualDirActualPlr)
            tag.putConnectionMode(MODE, info.mode)
            tag.putLocatorSet(POS, locator)

            return tag
        }

        companion object {
            private const val DIR = "dir"
            private const val MODE = "mode"
            private const val POS = "pos"

            fun fromNbt(tag: CompoundTag): Connection {
                return Connection(
                    CellPartConnectionInfo(
                        tag.getConnectionMode(MODE),
                        tag.getDirectionActual(DIR),
                    ),
                    tag.getLocatorSet(POS)
                )
            }
        }
    }

    private val connections = HashSet<Connection>()
    private var temperature = 0.0
    private var connectionsChanged = true

    override fun createRenderer(): WirePartRenderer {
        val renderInfo = (renderInfo ?: error("Render info is null")).get()
        return WirePartRenderer(this, renderInfo.meshes, renderInfo.color, radiatesGameLight)
    }

    override fun initializeRenderer() {
        val directionArray = IntArray(connections.size)

        var i = 0
        connections.forEach {
            directionArray[i++] = it.info.data
        }

        renderer.updateDirections(directionArray)
    }

    override fun onPlaced() {
        super.onPlaced()

        if (!placement.level.isClientSide) {
            setSyncDirty()
        }
    }

    override fun loadFromTag(tag: CompoundTag) {
        super.loadFromTag(tag)

        loadConnectionsTag(tag)
    }

    override fun getSaveTag(): CompoundTag? {
        val tag = super.getSaveTag() ?: return null

        saveConnections(tag)

        return tag
    }

    override fun getSyncTag(): CompoundTag? {
        if (!connectionsChanged) {
            return null
        }

        return CompoundTag().also { tag ->
            saveConnections(tag)
        }
    }

    override fun handleSyncTag(tag: CompoundTag) {
        if (tag.contains(DIRECTIONS)) {
            loadConnectionsTag(tag)
        }
    }

    private fun updateLight() {
        if (!radiatesGameLight) {
            return
        }

        val baseLevel = lerp(
            COLD_LIGHT_LEVEL,
            HOT_LIGHT_LEVEL,
            map(
                temperature.coerceIn(COLD_LIGHT_TEMPERATURE, HOT_LIGHT_TEMPERATURE),
                COLD_LIGHT_TEMPERATURE,
                HOT_LIGHT_TEMPERATURE,
                0.0,
                1.0
            )
        )
        // todo
        //updateBrightness(baseLevel.toInt().coerceIn(0, 15))
    }

    private fun saveConnections(tag: CompoundTag) {
        val directionList = ListTag()

        connections.forEach { directionList.add(it.toNbt()) }

        tag.put(DIRECTIONS, directionList)
    }

    private fun loadConnectionsTag(tag: CompoundTag) {
        connections.clear()

        val directionList = tag.get(DIRECTIONS) as ListTag

        directionList.forEach {
            connections.add(Connection.fromNbt(it as CompoundTag))
        }

        if (placement.level.isClientSide) {
            initializeRenderer()
        }
    }

    override fun onConnected(remoteCell: Cell) {
        connectionsChanged = true

        val connectionInfo = solveCellPartConnection(cell, remoteCell)

        if (connectionInfo.mode == CellPartConnectionMode.Unknown) {
            error("Unhandled connection mode")
        }

        connections.add(Connection(connectionInfo, remoteCell.locator))

        setSyncAndSaveDirty()
    }

    override fun onDisconnected(remoteCell: Cell) {
        if(cell.isBeingRemoved) {
            // Don't send updates if we're being removed
            return
        }

        connectionsChanged = true
        connections.removeIf { it.locator == remoteCell.locator }
        setSyncAndSaveDirty()
    }

    override fun onCellAcquired() {
        startGameLightUpdates()
    }

    @ServerOnly
    private fun startGameLightUpdates() {
        if (!isAlive) {
            return
        }

        updateLight()

        schedulePre(20, this::startGameLightUpdates)
    }

    override fun registerPackets(builder: PacketHandlerBuilder) {
        builder.withHandler<Sync> {
            renderer.updateTemperature(it.temperature)
        }
    }

    override fun streamTemperatureChanges(bodies: List<ThermalBody>, dirty: List<ThermalBody>) {
        sendBulkPacket(Sync(bodies.first().temperatureKelvin))
    }

    @Serializable
    private data class Sync(val temperature: Double)
}

object WireMeshSets {
    // todo replace when models are available

    val electricalWireMap = mapOf(
        (Base6Direction3dMask.EMPTY) to PartialModels.ELECTRICAL_WIRE_CROSSING_EMPTY,
        (Base6Direction3dMask.FRONT) to PartialModels.ELECTRICAL_WIRE_CROSSING_SINGLE_WIRE,
        (Base6Direction3dMask.FRONT + Base6Direction3dMask.BACK) to PartialModels.ELECTRICAL_WIRE_STRAIGHT,
        (Base6Direction3dMask.FRONT + Base6Direction3dMask.LEFT) to PartialModels.ELECTRICAL_WIRE_CORNER,
        (Base6Direction3dMask.LEFT + Base6Direction3dMask.FRONT + Base6Direction3dMask.RIGHT) to PartialModels.ELECTRICAL_WIRE_CROSSING,
        (Base6Direction3dMask.HORIZONTALS) to PartialModels.ELECTRICAL_WIRE_CROSSING_FULL
    )

    val thermalWireMap = mapOf(
        (Base6Direction3dMask.EMPTY) to PartialModels.THERMAL_WIRE_CROSSING_EMPTY,
        (Base6Direction3dMask.FRONT) to PartialModels.THERMAL_WIRE_CROSSING_SINGLE_WIRE,
        (Base6Direction3dMask.FRONT + Base6Direction3dMask.BACK) to PartialModels.THERMAL_WIRE_STRAIGHT,
        (Base6Direction3dMask.FRONT + Base6Direction3dMask.LEFT) to PartialModels.THERMAL_WIRE_CORNER,
        (Base6Direction3dMask.LEFT + Base6Direction3dMask.FRONT + Base6Direction3dMask.RIGHT) to PartialModels.THERMAL_WIRE_CROSSING,
        (Base6Direction3dMask.HORIZONTALS) to PartialModels.THERMAL_WIRE_CROSSING_FULL
    )
}

class WirePartRenderer(
    val part: WirePart<*>,
    val meshes: Map<Base6Direction3dMask, PartialModel>,
    val radiantColor: RadiantBodyColor,
    val radiates: Boolean
) : PartRenderer() {
    private var modelInstance: ModelData? = null
    private var directionsUpdate = AtomicUpdate<IntArray>()
    private val temperatureUpdate = AtomicUpdate<Double>()

    fun updateTemperature(value: Double) = temperatureUpdate.setLatest(value)

    fun updateDirections(directions: IntArray) = directionsUpdate.setLatest(directions)

    override fun beginFrame() {
        selectModel()
        setTemperatureTint()
    }

    private fun selectModel() {
        directionsUpdate.consume { connections ->
            var mask = Base6Direction3dMask.EMPTY

            connections.forEach { connection ->
                mask += CellPartConnectionInfo(connection).actualDirActualPlr
            }

            // Here, we search for the correct configuration by just rotating all the cases we know.
            for ((mappingMask, model) in meshes) {
                val match = mappingMask.matchCounterClockWise(mask)

                if (match == -1) {
                    continue
                }

                val currentModel = this.modelInstance

                val tint = if(currentModel != null) {
                    Color(currentModel.r.toInt(), currentModel.g.toInt(), currentModel.b.toInt(), currentModel.a.toInt())
                } else {
                    null
                }

                createInstance(model, Quaternionf(AxisAngle4f((PI / 2.0 * match).toFloat(), Vector3f(0.0f, 1.0f, 0.0f))))

                if(radiates && tint != null) {
                    this.modelInstance?.setColor(tint)
                }

                return@consume
            }

            LOG.error("Wire did not handle cases: $mask")
        }
    }

    private fun setTemperatureTint() {
        if (!radiates) {
            return
        }

        temperatureUpdate.consume {
            modelInstance?.setColor(radiantColor.evaluate(Temperature(it)))
        }
    }

    private fun createInstance(model: PartialModel, rotation: Quaternionf) {
        modelInstance?.delete()

        val size = 1.5 / 16

        modelInstance = multipart.materialManager
            .defaultSolid()
            .material(Materials.TRANSFORMED)
            .getModel(model)
            .createInstance()
            .loadIdentity()
            .translate(part.placement.face.opposite.normal.toVec3() * size)
            .blockCenter()
            .translate(part.worldBoundingBox.center)
            .multiply(part.placement.face.rotation.toJoml().mul(part.facingRotation).mul(rotation).toMinecraft())
            .zeroCenter()

        multipart.relightPart(part)
    }

    override fun getModelsToRelight(): List<FlatLit<*>> {
        if (modelInstance != null) {
            return listOf(modelInstance!!)
        }

        return listOf()
    }

    override fun remove() {
        modelInstance?.delete()
    }
}
