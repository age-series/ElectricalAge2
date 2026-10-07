@file:Suppress("unused")

package org.eln2.mc.common.content

import dev.engine_room.flywheel.api.visual.DynamicVisual
import dev.engine_room.flywheel.lib.instance.InstanceTypes
import dev.engine_room.flywheel.lib.instance.TransformedInstance
import dev.engine_room.flywheel.lib.model.Models
import dev.engine_room.flywheel.lib.model.baked.PartialModel
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.InteractionResult
import org.ageseries.libage.data.*
import org.ageseries.libage.mathematics.geometry.Vector3d
import org.ageseries.libage.mathematics.rounded
import org.ageseries.libage.sim.ConnectionParameters
import org.ageseries.libage.sim.Pole
import org.ageseries.libage.sim.ThermalMassDefinition
import org.ageseries.libage.sim.electrical.ElectricalComponentSet
import org.ageseries.libage.sim.electrical.ElectricalConnectivityMap
import org.ageseries.libage.sim.electrical.ElectricalPin
import org.ageseries.libage.sim.electrical.Resistor
import org.eln2.mc.ClientOnly
import org.eln2.mc.CrossThreadAccess
import org.eln2.mc.MonopoleMap
import org.eln2.mc.PoleMap
import org.eln2.mc.ServerOnly
import org.eln2.mc.client.render.FlwModels
import org.eln2.mc.client.render.foundation.*
import org.eln2.mc.common.blocks.foundation.MultipartVisualizationContext
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.events.runPre
import org.eln2.mc.common.grids.GridConnectionCell
import org.eln2.mc.common.grids.GridMaterialCategory
import org.eln2.mc.common.grids.GridNode
import org.eln2.mc.common.network.serverToClient.ClientSidePacketHandlerBuilder
import org.eln2.mc.common.parts.foundation.GridCellPart
import org.eln2.mc.common.parts.foundation.PartCreateInfo
import org.eln2.mc.common.parts.foundation.PartUseInfo
import org.eln2.mc.integration.ComponentDisplay
import org.eln2.mc.integration.ComponentDisplayList
import org.eln2.mc.mathematics.Base6Direction3d

data class RelayOptions(
    val closedResistance: Quantity<Resistance>,
    val openResistance: Quantity<Resistance>,
    val thermalDef: ThermalMassDefinition,
    val thermalBreakdown: Quantity<Temperature>,
    val dielectricBreakdown: Quantity<Potential>,
    val maxSwitchingInterval: Int
)

interface RelayEventConsumer {
    @CrossThreadAccess
    fun onRelayStateChanged()
}

class RelayElectricalObject(
    cell: RelayCell,
    val powerMap: PoleMap,
    val signalMap: MonopoleMap
) : ElectricalObject<RelayCell>(cell) {
    val powerResistor = Resistor()
    val signalResistor = Resistor().also { it.resistance = 1e8 }

    private var switchCooldown = 0

    override fun offerPolar(remote: ElectricalObject<*>): ElectricalPin? {
        when(powerMap.evaluateOrNull(cell, remote.cell)) {
            Pole.Positive -> return powerResistor.positive
            Pole.Negative -> return powerResistor.negative
            else -> { }
        }

        if(signalMap.evaluates(cell, remote.cell)) {
            return signalResistor.positive
        }

        return null
    }

    override fun offerTerminal(gc: GridConnectionCell, m0: GridConnectionCell.NodeInfo) = signalResistor.positive

    override fun addComponents(circuit: ElectricalComponentSet) {
        circuit.add(powerResistor)
        circuit.add(signalResistor)
    }

    override fun build(map: ElectricalConnectivityMap) {
        super.build(map)
        map.ground(signalResistor.negative)
    }

    override fun subscribe(subscribers: SubscriberCollection<SimulationPhase>) {
        subscribers.addPost(this::tick)
    }

    fun tick(dt: Double, phase: SimulationPhase) {
        val signal = signalResistor.potential
        val aboveThreshold = signal > cell.threshold
        val desiredClosed = if(cell.inverted) !aboveThreshold else aboveThreshold

        if(desiredClosed != cell.isClosed && switchCooldown <= 0) {
            cell.isClosed = desiredClosed
            cell.updateResistance()
            cell.setChanged()
            cell.eventConsumer?.onRelayStateChanged()
            switchCooldown = cell.options.maxSwitchingInterval
        }

        if(switchCooldown > 0) {
            switchCooldown--
        }
    }
}

class RelayCell(
    ci: CellCreateInfo,
    val powerMap: PoleMap,
    val powerWireSize: ElectricalSize,
    val signalMap: MonopoleMap,
    val options: RelayOptions,
    val leakage: ConnectionParameters
) : Cell(ci), SidedElectrical<RelayCell> {
    companion object {
        private const val THRESHOLD = "threshold"
        private const val INVERTED = "inverted"
        private const val IS_CLOSED = "isClosed"
    }

    var threshold = 50.0
        set(value) {
            field = value.coerceIn(-MAX_SIGNAL, MAX_SIGNAL)
            setChanged()
        }

    @CrossThreadAccess
    var inverted = false
        set(value) {
            field = value
            setChanged()
        }

    @CrossThreadAccess
    var isClosed = false

    var eventConsumer: RelayEventConsumer? = null
        private set

    fun bind(consumer: RelayEventConsumer) {
        require(eventConsumer == null)
        eventConsumer = consumer
    }

    fun unbind() {
        eventConsumer = null
    }


    override fun thermalObjectPredicate(remote: ThermalObject<*>) = false

    @SimObject
    val electrical = RelayElectricalObject(this, powerMap, signalMap).also {
        it.powerResistor.resistance = !options.openResistance
    }

    @SimObject
    val thermal = ThermalWireObject(this, options.thermalDef(), leakage)

    @Behavior
    val thermalBreakdown = ThermalBreakdownBehavior.create(options.thermalBreakdown, this) {
        thermal.thermalBody.temperature
    }

    @Behavior
    val dielectricBreakdown = DielectricBreakdownBehavior.create(this).also {
        it.addPort(
            electrical.powerResistor,
            !options.dielectricBreakdown,
            !options.dielectricBreakdown
        )
    }

    @Node
    val grid = GridNode(this)

    fun updateResistance() {
        val target = if(isClosed) {
            !options.closedResistance
        } else {
            !options.openResistance
        }

        electrical.powerResistor.updateResistance(target)
    }

    override fun subscribe(subscribers: SubscriberCollection<SimulationPhase>) {
        subscribers.addPost(this::tickPost)
    }

    private fun tickPost(dt: Double, phase: SimulationPhase) {
        thermal.thermalBody.energy += Quantity(electrical.powerResistor.power * dt, JOULE)
    }

    override fun getElectricalSizeOnSide(side: Base6Direction3d, targetCell: Cell): ElectricalSize? {
        if(powerMap.evaluateOrNull(this, targetCell) != null) {
            return powerWireSize
        }

        if(signalMap.evaluates(this, targetCell)) {
            return ElectricalSize.Signal
        }

        return null
    }

    override fun saveCellData() = CompoundTag().also {
        it.putDouble(THRESHOLD, threshold)
        it.putBoolean(INVERTED, inverted)
        it.putBoolean(IS_CLOSED, isClosed)
    }

    override fun loadCellData(tag: CompoundTag) {
        threshold = tag.getDouble(THRESHOLD)
        inverted = tag.getBoolean(INVERTED)
        isClosed = tag.getBoolean(IS_CLOSED)
        updateResistance()
    }

    fun submitDisplay(builder: ComponentDisplayList) {
        builder.translateQuantityRow("relay_threshold", Quantity(threshold, VOLT))
        builder.signalInput(!electrical.signalResistor.readouts.potential)
        builder.translateBoolean("relay_closed", isClosed)
        builder.translateBoolean("relay_inverted", inverted)
        builder.quantity(thermal.thermalBody.temperature)
        builder.quantity(electrical.powerResistor.readouts.potential)
        builder.quantity(electrical.powerResistor.readouts.current)
        builder.quantity(electrical.powerResistor.readouts.power)
    }
}

class RelayPart(
    ci: PartCreateInfo,
    val body: PartialModel,
    val contactModel: PartialModel,
    val closedIncrement: Vector3d,
    val models: Map<Base6Direction3d, WireConnectionModelPartial>,
    provider: CellProvider<RelayCell>
) :
    GridCellPart<RelayCell>(ci, provider),
    ComponentDisplay,
    PartWithKnobs,
    ScrewdriverScrollable,
    ScrewdriverInteractable,
    ConnectedPart,
    RelayEventConsumer
{
    override val knobMap = KnobMap(this::onKnobMapChanged)

    val knobThreshold = knobMap.addKnobBB(
        this,
        FlwModels.POTENTIAL_PROBE_KNOB_INPUT_RANGE_MIN,
        Vector3d.unitY,
        "knob_threshold",
        6.35, 2.025, 5.125,
        0.325, 0.45, 0.325
    ).configure {
        setLimits(-MAX_SIGNAL, MAX_SIGNAL)
        makeInteractable("waila.eln2.relay_threshold")
    }

    val terminal = defineCellBoxTerminalBB(
        10.6, 0.775, 7.85,
        0.3, 0.55, 0.3,
        highlightColor = MyColor.BLUE,
        categories = listOf(GridMaterialCategory.SignalGrid)
    )

    @ClientOnly
    var isClosed = false
        private set

    @ClientOnly
    var inverted = false
        private set

    @ClientOnly
    private var renderStateImpl = ConnectedPartRenderStateImpl.createIfApplicable(this)

    @ClientOnly
    override val connectedRenderState: ConnectedPartRenderState get() = renderStateImpl!!

    override fun getSyncTag() = ConnectedPart.pack(this).also {
        it.putBoolean(IS_CLOSED, cell.isClosed)
        it.putBoolean(INVERTED, cell.inverted)
    }

    @ClientOnly
    override fun handleSyncTag(tag: CompoundTag) {
        renderStateImpl!!.set(getConnectedPartsFromTag(tag))
        isClosed = tag.getBoolean(IS_CLOSED)
        inverted = tag.getBoolean(INVERTED)
    }

    override fun onConnectivityChanged() = this.setSyncDirty()

    override fun createVisual(ctx: MultipartVisualizationContext) = RelayPartVisual(
        ctx, this, body, contactModel, closedIncrement, models
    )

    override fun onCellAcquired() {
        cell.bind(this)

        knobMap.loadChanges {
            knobThreshold.rotation = cell.threshold
        }
    }

    override fun onCellReleased() {
        cell.unbind()
    }

    private fun onKnobMapChanged() {
        if(!placement.level.isClientSide) {
            if(hasCell) {
                cell.threshold = knobThreshold.rotation
                sendBulkPacket(KnobMap.SyncPacket::serialize, knobMap.getSyncPacket())
            }
        }
    }

    @ServerOnly
    override fun onSyncSuggested() {
        super.onSyncSuggested()
        sendBulkPacket(KnobMap.SyncPacket::serialize, knobMap.getSyncPacket())
    }

    @ClientOnly
    override fun setupPacketsOnClient(builder: ClientSidePacketHandlerBuilder) {
        builder.withHandler<KnobMap.SyncPacket>(KnobMap.SyncPacket::deserialize) {
            knobMap.loadSyncPacket(it)
        }
    }

    @ServerOnly
    override fun scrollScrewdriver(player: ServerPlayer, delta: Double): Boolean {
        return knobMap.screwdriverInteraction(player, delta)
    }

    @ServerOnly
    override fun applyScrewdriver(screwdriver: ScrewdriverItem, context: UseOnContext, configValue: OptionalDouble) {
        knobMap.screwdriverConfigure(context.player as ServerPlayer, configValue)
    }

    override fun onUsedBy(context: PartUseInfo): InteractionResult {
        if(placement.level.isClientSide) {
            return InteractionResult.SUCCESS
        }

        cell.inverted = !cell.inverted
        setSyncDirty()

        return InteractionResult.SUCCESS
    }

    @CrossThreadAccess
    override fun onRelayStateChanged() {
        runPre {
            setSyncDirty()
        }
    }

    override fun submitDisplay(builder: ComponentDisplayList) {
        builder.debugInIDE { "Threshold knob: ${knobThreshold.rotation.rounded()}" }
        cell.submitDisplay(builder)
    }

    companion object {
        private const val IS_CLOSED = "isClosed"
        private const val INVERTED = "inverted"
    }
}

class RelayPartVisual(
    ctx: MultipartVisualizationContext,
    part: RelayPart,
    body: PartialModel,
    contactModel: PartialModel,
    val closedIncrement: Vector3d,
    connectionModels: Map<Base6Direction3d, WireConnectionModelPartial>
) : ConnectedPartWithKnobsVisual<RelayPart>(ctx, part, body, connectionModels) {

    private val contact: TransformedInstance = visualizationContext.instancerProvider()
        .instancer(InstanceTypes.TRANSFORMED, Models.partial(contactModel))
        .createInstance()

    private var trackedIsClosed = part.isClosed

    init {
        poseContact()
    }

    private fun poseContact() {
        contact.setIdentityTransform().partTransformation(visualizationContext.parent, part)

        if(part.isClosed) {
            contact.translate(closedIncrement.x, closedIncrement.y, closedIncrement.z)
        }

        contact.handle().setChanged()
    }

    override fun beginFrame(ctx: DynamicVisual.Context) {
        super.beginFrame(ctx)

        if(part.isClosed != trackedIsClosed) {
            trackedIsClosed = part.isClosed
            poseContact()
        }
    }

    override fun updateLight(partialTick: Float) {
        super.updateLight(partialTick)
        visualizationContext.parent.relightInstances(contact)
    }

    override fun _delete() {
        super._delete()
        contact.delete()
    }
}
