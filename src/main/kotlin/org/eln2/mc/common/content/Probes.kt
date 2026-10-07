package org.eln2.mc.common.content

import dev.engine_room.flywheel.lib.model.baked.PartialModel
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import org.ageseries.libage.data.classify
import org.ageseries.libage.mathematics.geometry.Vector3d
import org.ageseries.libage.mathematics.map
import org.ageseries.libage.mathematics.rounded
import org.ageseries.libage.sim.Pole
import org.ageseries.libage.sim.electrical.ElectricalComponentSet
import org.ageseries.libage.sim.electrical.ElectricalConnectivityMap
import org.ageseries.libage.sim.electrical.ElectricalPin
import org.ageseries.libage.sim.electrical.Resistor
import org.eln2.mc.ClientOnly
import org.eln2.mc.MonopoleMap
import org.eln2.mc.PoleMap
import org.eln2.mc.ServerOnly
import org.eln2.mc.client.render.FlwModels
import org.eln2.mc.client.render.foundation.*
import org.eln2.mc.common.blocks.foundation.MultipartVisualizationContext
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.grids.GridConnectionCell
import org.eln2.mc.common.grids.GridMaterialCategory
import org.eln2.mc.common.grids.GridNode
import org.eln2.mc.common.network.serverToClient.ClientSidePacketHandlerBuilder
import org.eln2.mc.common.parts.foundation.GridCellPart
import org.eln2.mc.common.parts.foundation.PartCreateInfo
import org.eln2.mc.integration.ComponentDisplay
import org.eln2.mc.integration.ComponentDisplayList
import org.eln2.mc.mathematics.Base6Direction3d
import net.minecraft.world.item.context.UseOnContext
import org.ageseries.libage.data.OptionalDouble

/**
 * The bound for signal ([-[MAX_SIGNAL], +[MAX_SIGNAL]]).
 * */
const val MAX_SIGNAL = 100.0 // Volts

/**
 * Maps a measured quantity (e.g. potential, current, power) to the output signal.
 * */
fun interface ProbeSignalMap {
    fun getSignal(measuredQuantity: Double): Double
}

/**
 * Linearly maps the measured quantity from a set range [[inputLow], [inputHigh]] to [[outputLow], [outputHigh]] with extra numerical safeguards.
 * NaN and Infinity results are smashed to 0. Could be caused by invalid ranges (which are set by players).
 * The result is also clamped to [-[MAX_SIGNAL], +[MAX_SIGNAL]].
 * Also offers utility methods for saving the setpoints to NBT.
 * */
class ProbeRangeToRangeMap : ProbeSignalMap {
    var inputLow = 0.0
    var inputHigh = 1.0
    var outputLow = 0.0
    var outputHigh = 1.0

    override fun getSignal(measuredQuantity: Double): Double {
        var result = map(measuredQuantity, inputLow, inputHigh, outputLow, outputHigh)

        if(result.isInfinite() || result.isNaN()) {
            result = 0.0
        }

        result = result.coerceIn(-MAX_SIGNAL, +MAX_SIGNAL)

        return result
    }

    fun saveToTag(tag: CompoundTag) {
        tag.putDouble(INPUT_LOW, inputLow)
        tag.putDouble(INPUT_HIGH, inputHigh)
        tag.putDouble(OUTPUT_LOW, outputLow)
        tag.putDouble(OUTPUT_HIGH, outputHigh)
    }

    fun loadFromTag(tag: CompoundTag) {
        inputLow = tag.getDouble(INPUT_LOW)
        inputHigh = tag.getDouble(INPUT_HIGH)
        outputLow = tag.getDouble(OUTPUT_LOW)
        outputHigh = tag.getDouble(OUTPUT_HIGH)
    }

    companion object {
        private const val INPUT_LOW = "inputLow"
        private const val INPUT_HIGH = "inputHigh"
        private const val OUTPUT_LOW = "outputLow"
        private const val OUTPUT_HIGH = "outputHigh"
    }
}

/**
 * Generic probe object. Consists of a signal generator, and a single internal resistor that is mapped to two sides of the probe.
 * The internal resistor may be used as a:
 * - Potential Probe - when the resistance is set high (near max resistance).
 * - Current Probe - when the resistance is set low (~wire).
 *
 * The signal output is implicitly mapped to any terminal request.
 *
 * @param comparerMap The pole map used to map the two poles of the [internalResistor]. The poles specified next affect the sign of the measurements.
 * @param outputMap The pole map used to map the single output of the generator.
 * @param signalMap The map from measured quantities to the output signal.
 * */
abstract class PassthroughElectricalProbeObject(
    cell: ElectricalProbeCell,
    val comparerMap: PoleMap,
    val outputMap: MonopoleMap,
    val signalMap: ProbeSignalMap
) : ElectricalObject<ElectricalProbeCell>(cell) {
    val signalSource = SignalSource()

    /**
     * The internal resistor, mapped by [comparerMap].
     * Its resistance should be set based on the type of probe.
     * */
    val internalResistor = Resistor()

    override fun offerPolar(remote: ElectricalObject<*>) : ElectricalPin? {
        when(comparerMap.evaluateOrNull(cell, remote.cell)) {
            Pole.Positive -> return internalResistor.positive
            Pole.Negative -> return internalResistor.negative
            else -> { /* ignored */ }
        }

        if(outputMap.evaluates(cell, remote.cell)) {
            return signalSource.offerOutput()
        }

        return null
    }

    override fun offerTerminal(gc: GridConnectionCell, m0: GridConnectionCell.NodeInfo) = signalSource.offerOutput()

    override fun addComponents(circuit: ElectricalComponentSet) {
        circuit.add(signalSource)
        circuit.add(internalResistor)
    }

    override fun build(map: ElectricalConnectivityMap) {
        super.build(map)
        signalSource.build(map)
    }

    override fun subscribe(subscribers: SubscriberCollection<SimulationPhase>) {
        subscribers.addPost(this::tick)
    }

    /**
     * Gets the quantity passed to the [signalMap] every tick.
     * */
    abstract fun getMeasuredQuantity(dt: Double, subscriberPhase: SimulationPhase) : Double

    /**
     * Sets the signal with the measurements from the [internalResistor].
     * */
    fun tick(dt: Double, subscriberPhase: SimulationPhase) {
        val quantity = getMeasuredQuantity(dt, subscriberPhase)
        val signal = signalMap.getSignal(quantity)
        signalSource.signal = signal
    }
}

class PotentialProbeObject(
    cell: ElectricalProbeCell,
    comparerMap: PoleMap,
    outputMap: MonopoleMap,
    signalMap: ProbeSignalMap
) : PassthroughElectricalProbeObject(cell, comparerMap, outputMap, signalMap) {
    init { internalResistor.resistance = 1e7 }

    override fun getMeasuredQuantity(dt: Double, subscriberPhase: SimulationPhase) = internalResistor.potential
}

class CurrentProbeObject(
    cell: ElectricalProbeCell,
    comparerMap: PoleMap,
    outputMap: MonopoleMap,
    signalMap: ProbeSignalMap
) : PassthroughElectricalProbeObject(cell, comparerMap, outputMap, signalMap) {
    init { internalResistor.resistance = 1e-5 }

    override fun getMeasuredQuantity(dt: Double, subscriberPhase: SimulationPhase) = internalResistor.current
}

enum class ElectricalProbeType {
    /**
     * Uses a [PotentialProbeObject] and measures potential across the (high-resistance) resistor.
     * */
    Potential,
    /**
     * Uses a [CurrentProbeObject] and measures current across the (low-resistance) resistor.
     * */
    Current
}

class ElectricalProbeCell(
    ci: CellCreateInfo,
    comparerMap: PoleMap,
    val comparerWireSize: ElectricalSize,
    outputMap: MonopoleMap,
    type: ElectricalProbeType
) : Cell(ci), SidedElectrical<ElectricalProbeCell> {
    val signalMap = ProbeRangeToRangeMap()

    @SimObject
    val probe: PassthroughElectricalProbeObject = when(type) {
        ElectricalProbeType.Potential -> PotentialProbeObject(this, comparerMap, outputMap, signalMap)
        ElectricalProbeType.Current -> CurrentProbeObject(this, comparerMap, outputMap, signalMap)
    }

    @Node
    val grid = GridNode(this)

    override fun saveCellData() = CompoundTag().also {
        signalMap.saveToTag(it)
    }

    override fun loadCellData(tag: CompoundTag) {
        signalMap.loadFromTag(tag)
    }

    override fun getElectricalSizeOnSide(side: Base6Direction3d, targetCell: Cell): ElectricalSize? {
        if(probe.comparerMap.evaluateOrNull(this, targetCell) != null) {
            return comparerWireSize
        }

        if(probe.outputMap.evaluates(this, targetCell)) {
            return ElectricalSize.Signal
        }

        return null
    }

    fun submitDisplay(builder: ComponentDisplayList) {
        builder.debugInIDE { "In: [${signalMap.inputLow.rounded()}, ${signalMap.inputHigh.rounded()}], Out: [${signalMap.outputLow}, ${signalMap.outputHigh}]" }
        builder.debugInIDE { "Power: ${probe.internalResistor.readouts.power.classify()}" }
        builder.signalOutput(probe.signalSource.signal)
        builder.quantity(probe.internalResistor.readouts.potential)
        builder.quantity(probe.internalResistor.readouts.current)
    }
}

class ElectricalProbePart(
    ci: PartCreateInfo,
    val body: PartialModel,
    val models: Map<Base6Direction3d, WireConnectionModelPartial>,
    provider: CellProvider<ElectricalProbeCell>
) :
    GridCellPart<ElectricalProbeCell>(ci, provider),
    ComponentDisplay,
    PartWithKnobs,
    ScrewdriverScrollable,
    ScrewdriverInteractable,
    ConnectedPart
{
    override val knobMap = KnobMap(this::onKnobMapChanged)

    val knobInputRangeMin = knobMap.addKnobBB(
        this,
        FlwModels.POTENTIAL_PROBE_KNOB_INPUT_RANGE_MIN,
        Vector3d.unitY,
        "knob_input_range_min",
        6.35, 2.025, 5.125,
        0.325, 0.45, 0.325
    ).configure { setLimits(-MAX_SIGNAL, MAX_SIGNAL); makeInteractable("waila.eln2.signal_input_min") }

    val knobInputRangeMax = knobMap.addKnobBB(
        this,
        FlwModels.POTENTIAL_PROBE_KNOB_INPUT_RANGE_MAX,
        Vector3d.unitY,
        "knob_input_range_max",
        6.35, 2.025, 5.625,
        0.325, 0.45, 0.325
    ).configure { setLimits(-MAX_SIGNAL, MAX_SIGNAL); makeInteractable("waila.eln2.signal_input_max") }

    val knobOutputRangeMin = knobMap.addKnobBB(
        this,
        FlwModels.POTENTIAL_PROBE_KNOB_OUTPUT_RANGE_MIN,
        Vector3d.unitY,
        "knob_output_range_min",
        6.35, 2.025, 6.125,
        0.325, 0.45, 0.325
    ).configure { setLimits(-MAX_SIGNAL, MAX_SIGNAL); makeInteractable("waila.eln2.signal_output_min") }

    val knobOutputRangeMax = knobMap.addKnobBB(
        this,
        FlwModels.POTENTIAL_PROBE_KNOB_OUTPUT_RANGE_MAX,
        Vector3d.unitY,
        "knob_output_range_max",
        6.35, 2.025, 6.625,
        0.325, 0.45, 0.325
    ).configure { setLimits(-MAX_SIGNAL, MAX_SIGNAL); makeInteractable("waila.eln2.signal_output_max") }

    val terminal = defineCellBoxTerminalBB(
        10.6, 0.775, 7.85,
        0.3, 0.55, 0.3,
        highlightColor = MyColor.BLUE,
        categories = listOf(GridMaterialCategory.SignalGrid)
    )

    @ClientOnly
    private var renderStateImpl = ConnectedPartRenderStateImpl.createIfApplicable(this)

    @ClientOnly
    override val connectedRenderState: ConnectedPartRenderState get() = renderStateImpl!!

    override fun getSyncTag() = ConnectedPart.pack(this)

    override fun handleSyncTag(tag: CompoundTag) = renderStateImpl!!.set(getConnectedPartsFromTag(tag))

    override fun onConnectivityChanged() = this.setSyncDirty()

    override fun createVisual(ctx: MultipartVisualizationContext) = ConnectedPartWithKnobsVisual(
        ctx, this, body, models
    )

    /**
     * We store the knob states in the cell (simulation domain), so we copy them into the knob map here.
     * */
    override fun onCellAcquired() {
        knobMap.loadChanges {
            knobInputRangeMin.rotation = cell.signalMap.inputLow
            knobInputRangeMax.rotation = cell.signalMap.inputHigh
            knobOutputRangeMin.rotation = cell.signalMap.outputLow
            knobOutputRangeMax.rotation = cell.signalMap.outputHigh
        }
    }

    /**
     * Called on both the client and the server, when knobs change states. We use it only on the server.
     * Here, we apply the new rotations to the cell storage, and send a sync packet to the client.
     * */
    private fun onKnobMapChanged() {
        if(!placement.level.isClientSide) {
            if(hasCell) {
                cell.signalMap.inputLow = knobInputRangeMin.rotation
                cell.signalMap.inputHigh = knobInputRangeMax.rotation
                cell.signalMap.outputLow = knobOutputRangeMin.rotation
                cell.signalMap.outputHigh = knobOutputRangeMax.rotation
                cell.setChanged()
            }

            sendBulkPacket(KnobMap.SyncPacket::serialize, knobMap.getSyncPacket())
        }
    }

    @ServerOnly
    override fun onSyncSuggested() {
        super.onSyncSuggested() // connected part
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

    override fun submitDisplay(builder: ComponentDisplayList) {
        builder.debugInIDE {
            "K0: ${knobInputRangeMin.rotation.rounded()}, " +
            "K1: ${knobInputRangeMax.rotation.rounded()}, " +
            "K2: ${knobOutputRangeMin.rotation.rounded()}, " +
            "K3: ${knobOutputRangeMax.rotation.rounded()}"
        }

        cell.submitDisplay(builder)
    }
}
