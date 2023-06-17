@file:Suppress("NonAsciiCharacters")

package org.eln2.mc.common.content

import com.jozufozu.flywheel.core.Materials
import com.jozufozu.flywheel.core.PartialModel
import com.jozufozu.flywheel.core.materials.FlatLit
import com.jozufozu.flywheel.core.materials.model.ModelData
import com.mojang.math.Quaternion
import com.mojang.math.Vector3f
import kotlinx.serialization.Serializable
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.ageseries.libage.sim.Material
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.thermal.*
import net.minecraft.core.BlockPos
import org.eln2.mc.*
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.client.render.foundation.MultipartBlockEntityInstance
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.foundation.defaultRadiantBodyColor
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.cells.foundation.CellProvider
import org.eln2.mc.common.events.AtomicUpdate
import org.eln2.mc.common.events.EventScheduler
import org.eln2.mc.common.network.serverToClient.PacketHandlerBuilder
import org.eln2.mc.common.parts.foundation.*
import org.eln2.mc.data.*
import org.eln2.mc.integration.WailaEntity
import org.eln2.mc.mathematics.*
import org.eln2.mc.mathematics.cylinderSurfaceArea
import org.eln2.mc.sim.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.absoluteValue

/**
 * The [ElectricalWireObject] has a single [ResistorBundle]. The Internal Pins of the bundle are connected to each other, and
 * the External Pins are exported to other Electrical Objects.
 * */
class ElectricalWireObject(cell: Cell) : ElectricalObject(cell) {
    private val resistors = ResistorBundle(0.05, this)

    /**
     * Gets or sets the resistance of the bundle.
     * Only applied when the circuit is re-built.
     * */
    var resistance : Double = resistors.resistance
        set(value) {
            field = value
            resistors.resistance = value
        }

    val power get() = resistors.power

    override fun offerComponent(neighbour: ElectricalObject): ElectricalComponentInfo {
        return resistors.getOfferedResistor(neighbour)
    }

    override fun clearComponents() {
        resistors.clear()
    }

    override fun addComponents(circuit: Circuit) {
        resistors.register(connections, circuit)
    }

    override fun build() {
        resistors.connect(connections, this)

        // Is there a better way to do this?

        // The Wire uses a bundle of 4 resistors. Every resistor's "Internal Pin" is connected to every
        // other resistor's internal pin. "External Pins" are offered to connection candidates.

        resistors.process { a ->
            resistors.process { b ->
                if (a != b) {
                    a.connect(CellConvention.INTERNAL_PIN, b, CellConvention.INTERNAL_PIN)
                }
            }
        }
    }
}

class ThermalWireObject(cell: Cell, def: ThermalBodyDef) : ThermalObject(cell), WailaEntity, PersistentObject, DataEntity {
    companion object {
        private const val THERMAL_BODY = "thermalBody"
    }

    constructor(cell: Cell): this(
        cell,
        ThermalBodyDef(
            Material.COPPER,
            mass = 1.0,
            area = cylinderSurfaceArea(1.0, 0.05)
        )
    )

    var body = def.create().also { b ->
        if(def.energy == null) {
            cell.envFldMap.read<EnvTemperatureField>()?.readTemperature()?.also {
                b.temp = it
            }
        }
    }

    override fun offerComponent(neighbour: ThermalObject): ThermalComponentInfo {
        return ThermalComponentInfo(body)
    }

    override fun addComponents(simulator: Simulator) {
        simulator.add(body)

        cell.envFldMap.readAll2<EnvTemperatureField, EnvThermalConductivityField>()?.also { (tempField, condField) ->
            simulator.connect(
                body,
                EnvironmentInformation(
                    tempField.readTemperature(),
                    condField.readConductivity()
                )
            )
        }
    }

    override fun save(): CompoundTag {
        return CompoundTag().also {
            it.putThermalBody(THERMAL_BODY, body)
        }
    }

    override fun load(tag: CompoundTag) {
        body = tag.getThermalBody(THERMAL_BODY)
    }

    override val dataNode = data {
        it.withField(TemperatureField {
            body.temp
        })

        it.withField(EnergyField {
            body.energy
        })
    }
}

class WireModel {
    var electricalResistance = 2.14 * 10e-6
    var material = Material.COPPER
    var replicateTemperature = false
    var isElectrical = true
    var damageThreshold = Temperature.from(500.0, ThermalUnits.CELSIUS)
}

class WireCell(ci: CellCreateInfo, val model: WireModel) : Cell(ci) {
    override val dataNode = data {
        if(model.isElectrical) {
            it.withField(ResistanceField {
                electricalWireObj!!.resistance
            })

            it.withField(PowerField {
                electricalWireObj!!.power.absoluteValue
            })
        }

        it.withField(TemperatureField(inspect = false) {
            thermalWireObj.body.temp
        })
    }

    @SimObject
    val thermalWireObj = ThermalWireObject(this)

    @SimObject
    val electricalWireObj =
        if(model.isElectrical){
            ElectricalWireObject(this).also {
                it.resistance = model.electricalResistance / 2.0 // Divide by two because bundle creates 2 resistors
            }
        }
        else null

    @Behavior
    val electricalBehaviorObj =
        if(model.isElectrical)
            activate<ElectricalPowerConverterBehavior>() * activate<ElectricalHeatTransferBehavior>(thermalWireObj.body)
        else null

    @Behavior
    val explosionBehavior = activate<TemperatureExplosionBehavior>(
        TemperatureExplosionBehaviorOptions(
            temperatureThreshold = model.damageThreshold.kelvin
        )
    )

    @Replicator
    fun replicator(thermalReplicator: ThermalReplicator) =
        if(model.replicateTemperature) ThermalReplicatorBehavior(
            listOf(thermalWireObj.body), thermalReplicator
        )
        else null

    val temperature get() = thermalWireObj.body.tempK
}

class WirePart(
    id: ResourceLocation,
    context: PartPlacementInfo,
    cellProvider: CellProvider,
    val model: WireModel
):
    CellPart<WirePartRenderer>(id, context, cellProvider),
    ThermalReplicator
{
    companion object {
        private const val DIRECTIONS = "directions"
        private const val COLD_LIGHT_LEVEL = 0.0
        private const val COLD_LIGHT_TEMPERATURE = 500.0
        private const val HOT_LIGHT_LEVEL = 5.0
        private const val HOT_LIGHT_TEMPERATURE = 1000.0
    }

    override val sizeActual = bbVec(8.0, 2.0, 8.0)

    private data class Connection(
        val directionActual: RelativeDir,
        val mode: CellPartConnectionMode,
        val remotePosWorld: BlockPos
    ) {
        fun toNbt(): CompoundTag {
            val tag = CompoundTag()

            tag.putRelativeDirection("Dir", directionActual)
            tag.putString("Mode", mode.name)
            tag.putBlockPos("Pos", remotePosWorld)

            return tag
        }

        companion object {
            fun fromNbt(tag: CompoundTag): Connection {
                return Connection(
                    tag.getDirectionActual("Dir"),
                    CellPartConnectionMode.valueOf(tag.getString("Mode")),
                    tag.getBlockPos("Pos")
                )
            }
        }
    }

    private val connectedDirections = HashSet<Connection>()
    private var wireRenderer: WirePartRenderer? = null
    private var temperature = 0.0
    private var connectionsChanged = true

    override fun createRenderer(): WirePartRenderer {
        wireRenderer = WirePartRenderer(this,
            if(model.isElectrical) WireMeshSets.electricalWireMap
            else WireMeshSets.thermalWireMap,
            model.replicateTemperature
        )

        applyRendererState()

        return wireRenderer!!
    }

    override fun destroyRenderer() {
        super.destroyRenderer()
        wireRenderer = null
    }

    override fun onPlaced() {
        super.onPlaced()

        if (!placement.level.isClientSide) {
            syncChanges()
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
        if(!connectionsChanged) {
            return null
        }

        return CompoundTag().also { tag ->
            saveConnections(tag)
        }
    }

    override fun handleSyncTag(tag: CompoundTag) {
        if(tag.contains(DIRECTIONS)) {
            loadConnectionsTag(tag)
        }
    }

    private fun updateLight() {
        if(!model.replicateTemperature) {
            return
        }

        val level = (
            lerp(
                COLD_LIGHT_LEVEL,
                HOT_LIGHT_LEVEL,
                map(temperature.coerceIn(COLD_LIGHT_TEMPERATURE, HOT_LIGHT_TEMPERATURE),
                    COLD_LIGHT_TEMPERATURE,
                    HOT_LIGHT_TEMPERATURE,
                    0.0,
                    1.0)
                )
            )
            .toInt()
            .coerceIn(0, 15)

        updateBrightness(level)
    }

    private fun saveConnections(tag: CompoundTag) {
        val directionList = ListTag()

        connectedDirections.forEach { directionList.add(it.toNbt()) }

        tag.put(DIRECTIONS, directionList)
    }

    private fun loadConnectionsTag(tag: CompoundTag) {
        connectedDirections.clear()

        val directionList = tag.get(DIRECTIONS) as ListTag

        directionList.forEach { connectedDirections.add(Connection.fromNbt(it as CompoundTag)) }

        if (placement.level.isClientSide) {
            applyRendererState()
        }
    }

    private fun applyRendererState() {
        wireRenderer?.applyDirections(connectedDirections.map { it.directionActual }.toList())
    }

    override fun onConnected(remoteCell: Cell) {
        connectionsChanged = true

        val connectionInfo = solveCellPartConnection(cell, remoteCell)

        if(connectionInfo.mode == CellPartConnectionMode.Unknown) {
            error("Unhandled connection mode")
        }

        connectedDirections.add(
            Connection(
                connectionInfo.actualDirActualPlr,
                connectionInfo.mode,
                remoteCell.posDescr.requireLocator<R3, BlockPosLocator>().pos
            )
        )

        syncAndSave()
    }

    override fun onDisconnected(remoteCell: Cell) {
        connectionsChanged = true
        connectedDirections.removeIf { it.remotePosWorld == remoteCell.posDescr.requireLocator<R3, BlockPosLocator>().pos }
        syncAndSave()
    }

    override fun onCellAcquired() {
        startGameLightUpdates()
    }

    @ServerOnly
    private fun startGameLightUpdates() {
        if(!isAlive) {
            return
        }

        updateLight()

        EventScheduler.scheduleWorkPre(20, this::startGameLightUpdates)
    }

    override fun registerPackets(builder: PacketHandlerBuilder) {
        builder.withHandler<Sync> {
            renderer.updateTemperature(it.temp)
        }
    }

    override fun streamTemperatureChanges(bodies: List<ThermalBody>, dirty: List<ThermalBody>) {
        sendBulkPacket(Sync(bodies.first().tempK))
    }

    @Serializable
    private data class Sync(val temp: Double)
}

object WireMeshSets {
    // todo replace when models are available

    val electricalWireMap = mapOf(
        (DirectionMask.EMPTY) to PartialModels.ELECTRICAL_WIRE_CROSSING_EMPTY,
        (DirectionMask.FRONT) to PartialModels.ELECTRICAL_WIRE_CROSSING_SINGLE_WIRE,
        (DirectionMask.FRONT + DirectionMask.BACK) to PartialModels.ELECTRICAL_WIRE_STRAIGHT,
        (DirectionMask.FRONT + DirectionMask.LEFT) to PartialModels.ELECTRICAL_WIRE_CORNER,
        (DirectionMask.LEFT + DirectionMask.FRONT + DirectionMask.RIGHT) to PartialModels.ELECTRICAL_WIRE_CROSSING,
        (DirectionMask.HORIZONTALS) to PartialModels.ELECTRICAL_WIRE_CROSSING_FULL
    )

    val thermalWireMap = mapOf(
        (DirectionMask.EMPTY) to PartialModels.THERMAL_WIRE_CROSSING_EMPTY,
        (DirectionMask.FRONT) to PartialModels.THERMAL_WIRE_CROSSING_SINGLE_WIRE,
        (DirectionMask.FRONT + DirectionMask.BACK) to PartialModels.THERMAL_WIRE_STRAIGHT,
        (DirectionMask.FRONT + DirectionMask.LEFT) to PartialModels.THERMAL_WIRE_CORNER,
        (DirectionMask.LEFT + DirectionMask.FRONT + DirectionMask.RIGHT) to PartialModels.THERMAL_WIRE_CROSSING,
        (DirectionMask.HORIZONTALS) to PartialModels.THERMAL_WIRE_CROSSING_FULL
    )
}

class WirePartRenderer(
    val part: WirePart,
    val meshes: Map<DirectionMask, PartialModel>,
    val radiant: Boolean
):
    PartRenderer
{
    companion object {
        private val COLOR = defaultRadiantBodyColor()
    }

    override fun isSetupWith(multipartBlockEntityInstance: MultipartBlockEntityInstance): Boolean {
        return this::multipartInstance.isInitialized && this.multipartInstance == multipartBlockEntityInstance
    }

    private lateinit var multipartInstance: MultipartBlockEntityInstance
    private var modelInstance: ModelData? = null

    // Reset on every frame
    private var latestDirections = AtomicReference<List<RelativeDir>>()

    private val temperatureUpdate = AtomicUpdate<Double>()

    fun updateTemperature(value: Double) {
        temperatureUpdate.setLatest(value)
    }

    fun applyDirections(directions: List<RelativeDir>) {
        latestDirections.set(directions)
    }

    override fun setupRendering(multipart: MultipartBlockEntityInstance) {
        multipartInstance = multipart
    }

    override fun beginFrame() {
        selectModel()
        applyTemperatureRendering()
    }

    private fun selectModel() {
        val directions =
            latestDirections.getAndSet(null)
                ?: return

        val mask = DirectionMask.ofRelatives(directions)

        var found = false

        // Here, we search for the correct configuration by just rotating all the cases we know.
        meshes.forEach { (mappingMask, model) ->
            val match = mappingMask.matchCounterClockWise(mask)

            if (match != -1) {
                found = true

                updateInstance(model, Vector3f.YP.rotationDegrees(90f * match))

                return@forEach
            }
        }

        if (!found) {
            LOGGER.error("Wire did not handle cases: $directions")
        }

        applyTemperatureRendering()
    }

    private fun applyTemperatureRendering() {
        if(!radiant) {
            return
        }

        temperatureUpdate.consume {
            modelInstance?.setColor(COLOR.evaluate(Temperature(it)))
        }
    }

    private fun updateInstance(model: PartialModel, rotation: Quaternion) {
        modelInstance?.delete()

        val size = 1.5 / 16

        modelInstance = multipartInstance.materialManager
            .defaultSolid()
            .material(Materials.TRANSFORMED)
            .getModel(model)
            .createInstance()
            .loadIdentity()
            .translate(part.placement.face.opposite.normal.toVec3() * Vec3(size, size, size))
            .blockCenter()
            .translate(part.worldBoundingBox.center)
            .multiply(part.placement.face.rotation * part.txFacing * rotation)
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
