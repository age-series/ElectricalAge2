@file:Suppress("unused")

package org.eln2.mc.common.content

import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.context.UseOnContext
import org.ageseries.libage.data.OptionalDouble
import org.ageseries.libage.data.Potential
import org.ageseries.libage.data.Quantity
import org.ageseries.libage.data.VOLT
import org.ageseries.libage.mathematics.approxEq
import org.ageseries.libage.mathematics.geometry.Vector3d
import org.ageseries.libage.mathematics.rounded
import org.ageseries.libage.sim.electrical.ElectricalComponentSet
import org.ageseries.libage.sim.electrical.ElectricalConnectivityMap
import org.ageseries.libage.sim.electrical.ElectricalPin
import org.ageseries.libage.sim.electrical.Resistor
import org.eln2.mc.ClientOnly
import org.eln2.mc.CrossThreadAccess
import org.eln2.mc.MonopoleMap
import org.eln2.mc.ServerOnly
import org.eln2.mc.client.render.FlwModels
import org.eln2.mc.client.render.foundation.*
import org.eln2.mc.common.blocks.foundation.MultipartVisualizationContext
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.content.modules.Eln2Signal
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

//#region OpAmp

/**
 * Opamp that computes output = gain * (inputA +/- inputB).
 * Right-click toggles between addition and subtraction.
 * Inputs are read via high-resistance resistors (potential probes).
 * Output is a [SignalSource].
 * */
class SignalOpAmpElectricalObject(
    cell: SignalOpAmpCell,
    val inputAMap: MonopoleMap,
    val inputBMap: MonopoleMap,
    val outputMap: MonopoleMap
) : ElectricalObject<SignalOpAmpCell>(cell) {
    val inputAResistor = Resistor().also { it.resistance = SIGNAL_COMPARE_RESISTANCE }
    val inputBResistor = Resistor().also { it.resistance = SIGNAL_COMPARE_RESISTANCE }
    val signalSource = SignalSource()

    override fun offerPolar(remote: ElectricalObject<*>): ElectricalPin? {
        if(inputAMap.evaluates(cell, remote.cell)) {
            return inputAResistor.positive
        }

        if(inputBMap.evaluates(cell, remote.cell)) {
            return inputBResistor.positive
        }

        if(outputMap.evaluates(cell, remote.cell)) {
            return signalSource.offerOutput()
        }

        return null
    }

    override fun offerTerminal(gc: GridConnectionCell, m0: GridConnectionCell.NodeInfo): ElectricalPin? {
        return when(m0.terminal) {
            cell.inputATerminal -> inputAResistor.positive
            cell.inputBTerminal -> inputBResistor.positive
            cell.outputTerminal -> signalSource.offerOutput()
            else -> null
        }
    }

    override fun addComponents(circuit: ElectricalComponentSet) {
        circuit.add(inputAResistor)
        circuit.add(inputBResistor)
        circuit.add(signalSource)
    }

    override fun build(map: ElectricalConnectivityMap) {
        super.build(map)
        map.ground(inputAResistor.negative)
        map.ground(inputBResistor.negative)
        signalSource.build(map)
    }

    override fun subscribe(subscribers: SubscriberCollection<SimulationPhase>) {
        subscribers.addPost(this::tick)
    }

    fun tick(dt: Double, phase: SimulationPhase) {
        val inputA = inputAResistor.potential
        val inputB = inputBResistor.potential
        val combined = if(cell.subtractMode) inputA - inputB else inputA + inputB
        val output = (combined * cell.gain).coerceIn(-MAX_SIGNAL, MAX_SIGNAL)
        signalSource.signal = output
    }
}

class SignalOpAmpCell(
    ci: CellCreateInfo,
    inputAMap: MonopoleMap,
    inputBMap: MonopoleMap,
    outputMap: MonopoleMap,
    val inputATerminal: Int = 0,
    val inputBTerminal: Int = 1,
    val outputTerminal: Int = 2
) : Cell(ci), SidedElectrical<SignalOpAmpCell> {
    companion object {
        private const val GAIN = "gain"
        private const val SUBTRACT_MODE = "subtractMode"
    }

    var gain = 1.0
        set(value) {
            field = value.coerceIn(0.0, 10.0)
            setChanged()
        }

    @CrossThreadAccess
    var subtractMode = false
        set(value) {
            field = value
            setChanged()
        }

    @SimObject
    val opamp = SignalOpAmpElectricalObject(this, inputAMap, inputBMap, outputMap)

    @Node
    val grid = GridNode(this)

    override fun getElectricalSizeOnSide(side: Base6Direction3d, targetCell: Cell): ElectricalSize? {
        if(opamp.inputAMap.evaluates(this, targetCell) ||
            opamp.inputBMap.evaluates(this, targetCell) ||
            opamp.outputMap.evaluates(this, targetCell)) {
            return ElectricalSize.Signal
        }

        return null
    }

    override fun saveCellData() = CompoundTag().also {
        it.putDouble(GAIN, gain)
        it.putBoolean(SUBTRACT_MODE, subtractMode)
    }

    override fun loadCellData(tag: CompoundTag) {
        gain = tag.getDouble(GAIN)
        subtractMode = tag.getBoolean(SUBTRACT_MODE)
    }
}

class SignalOpAmpPart(ci: PartCreateInfo, provider: CellProvider<SignalOpAmpCell>) :
    GridCellPart<SignalOpAmpCell>(ci, provider),
    ComponentDisplay,
    PartWithKnobs,
    ScrewdriverScrollable,
    ScrewdriverInteractable,
    ConnectedPart
{
    override val knobMap = KnobMap(this::onKnobMapChanged)

    val knobGain = knobMap.addKnobBB(
        this,
        FlwModels.OPAMP_GAIN_KNOB,
        Vector3d.unitY,
        "knob_gain",
        5.85, 0.8, 5.675,
        0.325, 0.45, 0.325
    ).configure { setLimits(0.0, 10.0); makeInteractable("waila.eln2.opamp_gain") }

    val inputATerminal = defineCellBoxTerminalBB(
        4.7875, 0.775, 7.85,
        0.3, 0.55, 0.3,
        highlightColor = MyColor.RED,
        categories = listOf(GridMaterialCategory.SignalGrid)
    )

    val inputBTerminal = defineCellBoxTerminalBB(
        10.9, 0.775, 7.85,
        0.3, 0.55, 0.3,
        highlightColor = MyColor.BLUE,
        categories = listOf(GridMaterialCategory.SignalGrid)
    )

    val outputTerminal = defineCellBoxTerminalBB(
        7.8438, 0.775, 4.1437,
        0.3, 0.55, 0.3,
        highlightColor = MyColor.GREEN,
        categories = listOf(GridMaterialCategory.SignalGrid)
    )

    @ClientOnly
    var subtractMode = false
        private set

    @ClientOnly
    private var renderStateImpl = ConnectedPartRenderStateImpl.createIfApplicable(this)

    @ClientOnly
    override val connectedRenderState: ConnectedPartRenderState get() = renderStateImpl!!

    override fun getSyncTag() = ConnectedPart.pack(this).also {
        it.putBoolean(SUBTRACT_MODE, cell.subtractMode)
    }

    @ClientOnly
    override fun handleSyncTag(tag: CompoundTag) {
        renderStateImpl!!.set(getConnectedPartsFromTag(tag))
        subtractMode = tag.getBoolean(SUBTRACT_MODE)
    }

    override fun onConnectivityChanged() = this.setSyncDirty()

    override fun createVisual(ctx: MultipartVisualizationContext) = ConnectedPartWithKnobsVisual(
        ctx, this, FlwModels.OPAMP_BODY, Eln2Signal.SIGNAL_OPAMP_MODELS
    )

    override fun onCellAcquired() {
        knobMap.loadChanges {
            knobGain.rotation = cell.gain
        }
    }

    private fun onKnobMapChanged() {
        if(!placement.level.isClientSide) {
            if(hasCell) {
                cell.gain = knobGain.rotation
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
        if(!context.player.mainHandItem.isEmpty) {
            return InteractionResult.FAIL
        }

        if(placement.level.isClientSide) {
            return InteractionResult.SUCCESS
        }

        cell.subtractMode = !cell.subtractMode
        setSyncDirty()

        return InteractionResult.SUCCESS
    }

    override fun submitDisplay(builder: ComponentDisplayList) {
        builder.translateRow("opamp_mode", if (cell.subtractMode) "A - B" else "A + B")
        builder.translateRow("opamp_gain", cell.gain.rounded().toString())
        builder.signalInput(cell.opamp.inputAResistor.potential)
        builder.signalInput(cell.opamp.inputBResistor.potential)
        builder.signalOutput(cell.opamp.signalSource.signal)
    }

    companion object {
        private const val SUBTRACT_MODE = "subtractMode"
    }
}

//#endregion

//#region Signal Reference

/**
 * Signal reference that outputs a settable signal voltage in the [-100, 100] V range.
 * The value is set via a knob and fine-tuned via screwdriver interaction.
 * */
class SignalReferenceElectricalObject(cell: SignalReferenceCell, val outputMap: MonopoleMap) : ElectricalObject<SignalReferenceCell>(cell) {
    val signalSource = SignalSource()

    override fun offerPolar(remote: ElectricalObject<*>): ElectricalPin? {
        if(outputMap.evaluates(cell, remote.cell)) {
            return signalSource.offerOutput()
        }

        return null
    }

    override fun offerTerminal(gc: GridConnectionCell, m0: GridConnectionCell.NodeInfo) = signalSource.offerOutput()

    override fun addComponents(circuit: ElectricalComponentSet) {
        circuit.add(signalSource)
    }

    override fun build(map: ElectricalConnectivityMap) {
        super.build(map)
        signalSource.build(map)
    }

    override fun subscribe(subscribers: SubscriberCollection<SimulationPhase>) {
        subscribers.addPost(this::tick)
    }

    fun tick(dt: Double, phase: SimulationPhase) {
        signalSource.signal = cell.value
    }
}

class SignalReferenceCell(ci: CellCreateInfo, outputMap: MonopoleMap) : Cell(ci), SidedElectrical<SignalReferenceCell> {
    companion object {
        private const val VALUE = "value"
    }

    var value = 0.0
        set(value) {
            field = value.coerceIn(-MAX_SIGNAL, MAX_SIGNAL)
            setChanged()
        }

    @SimObject
    val reference = SignalReferenceElectricalObject(this, outputMap)

    @Node
    val grid = GridNode(this)

    override fun getElectricalSizeOnSide(side: Base6Direction3d, targetCell: Cell): ElectricalSize? {
        if(reference.outputMap.evaluates(this, targetCell)) {
            return ElectricalSize.Signal
        }

        return null
    }

    override fun saveCellData() = CompoundTag().also {
        it.putDouble(VALUE, value)
    }

    override fun loadCellData(tag: CompoundTag) {
        value = tag.getDouble(VALUE)
    }

    fun submitDisplay(builder: ComponentDisplayList) {
        builder.signalOutput(reference.signalSource.signal)
    }
}

class SignalReferencePart(ci: PartCreateInfo, provider: CellProvider<SignalReferenceCell>) :
    GridCellPart<SignalReferenceCell>(ci, provider),
    ComponentDisplay,
    PartWithKnobs,
    ScrewdriverScrollable,
    ScrewdriverInteractable,
    ConnectedPart {

    override val knobMap = KnobMap(this::onKnobMapChanged)

    val knobValue = knobMap.addKnobBB(
        this,
        FlwModels.SIGNAL_REFERENCE_VALUE_KNOB,
        Vector3d.unitY,
        "knob_value",
        6.2, 1.0, 6.175,
        0.325, 0.45, 0.325
    ).configure {
        setLimits(-MAX_SIGNAL, MAX_SIGNAL)
        makeInteractable("waila.eln2.signal_reference_value")
    }

    val outputTerminal = defineCellBoxTerminalBB(
        7.8, 1.0, 7.8,
        0.4, 1.0, 0.4,
        highlightColor = MyColor.GREEN,
        categories = listOf(GridMaterialCategory.SignalGrid)
    )

    @ClientOnly
    private var renderStateImpl = ConnectedPartRenderStateImpl.createIfApplicable(this)

    @ClientOnly
    override val connectedRenderState: ConnectedPartRenderState get() = renderStateImpl!!

    override fun getSyncTag() = ConnectedPart.pack(this)

    @ClientOnly
    override fun handleSyncTag(tag: CompoundTag) {
        renderStateImpl!!.set(getConnectedPartsFromTag(tag))
    }

    override fun onConnectivityChanged() = this.setSyncDirty()

    override fun createVisual(ctx: MultipartVisualizationContext) = ConnectedPartWithKnobsVisual(
        ctx, this, FlwModels.SIGNAL_REFERENCE_BODY, Eln2Signal.SIGNAL_REFERENCE_MODELS
    )

    override fun onCellAcquired() {
        knobMap.loadChanges {
            knobValue.rotation = cell.value
        }
    }

    private fun onKnobMapChanged() {
        if(!placement.level.isClientSide) {
            if(hasCell) {
                cell.value = knobValue.rotation
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

    override fun submitDisplay(builder: ComponentDisplayList) {
        cell.submitDisplay(builder)
    }
}

//#endregion

//#region Signal Clamper

/**
 * Clamps the input signal to the range `[clampMin, clampMax]`.
 * The output is a [SignalSource] set to the clamped value.
 * */
class SignalClamperElectricalObject(cell: SignalClamperCell, val inputMap: MonopoleMap, val outputMap: MonopoleMap) : ElectricalObject<SignalClamperCell>(cell) {
    val inputResistor = Resistor().also { it.resistance = SIGNAL_COMPARE_RESISTANCE }
    val signalSource = SignalSource()

    override fun offerPolar(remote: ElectricalObject<*>): ElectricalPin? {
        if(inputMap.evaluates(cell, remote.cell)) {
            return inputResistor.positive
        }

        if(outputMap.evaluates(cell, remote.cell)) {
            return signalSource.offerOutput()
        }

        return null
    }

    override fun offerTerminal(gc: GridConnectionCell, m0: GridConnectionCell.NodeInfo): ElectricalPin? {
        return when(m0.terminal) {
            cell.inputTerminal -> inputResistor.positive
            cell.outputTerminal -> signalSource.offerOutput()
            else -> null
        }
    }

    override fun addComponents(circuit: ElectricalComponentSet) {
        circuit.add(inputResistor)
        circuit.add(signalSource)
    }

    override fun build(map: ElectricalConnectivityMap) {
        super.build(map)
        map.ground(inputResistor.negative)
        signalSource.build(map)
    }

    override fun subscribe(subscribers: SubscriberCollection<SimulationPhase>) {
        subscribers.addPost(this::tick)
    }

    fun tick(dt: Double, phase: SimulationPhase) {
        val input = inputResistor.potential
        val min = cell.clampMin
        val max = cell.clampMax
        val clamped = if(min <= max) input.coerceIn(min, max) else input.coerceIn(max, min)
        signalSource.signal = clamped.coerceIn(-MAX_SIGNAL, MAX_SIGNAL)
    }
}

class SignalClamperCell(
    ci: CellCreateInfo,
    inputMap: MonopoleMap,
    outputMap: MonopoleMap,
    val inputTerminal: Int = 0,
    val outputTerminal: Int = 1
) : Cell(ci), SidedElectrical<SignalClamperCell> {
    companion object {
        private const val CLAMP_MIN = "clampMin"
        private const val CLAMP_MAX = "clampMax"
    }

    var clampMin = -MAX_SIGNAL
        set(value) {
            field = value.coerceIn(-MAX_SIGNAL, MAX_SIGNAL)
            setChanged()
        }

    var clampMax = MAX_SIGNAL
        set(value) {
            field = value.coerceIn(-MAX_SIGNAL, MAX_SIGNAL)
            setChanged()
        }

    @SimObject
    val clamper = SignalClamperElectricalObject(this, inputMap, outputMap)

    @Node
    val grid = GridNode(this)

    override fun getElectricalSizeOnSide(side: Base6Direction3d, targetCell: Cell): ElectricalSize? {
        if(clamper.inputMap.evaluates(this, targetCell) ||
            clamper.outputMap.evaluates(this, targetCell)) {
            return ElectricalSize.Signal
        }

        return null
    }

    override fun saveCellData() = CompoundTag().also {
        it.putDouble(CLAMP_MIN, clampMin)
        it.putDouble(CLAMP_MAX, clampMax)
    }

    override fun loadCellData(tag: CompoundTag) {
        clampMin = tag.getDouble(CLAMP_MIN)
        clampMax = tag.getDouble(CLAMP_MAX)
    }

}

class SignalClamperPart(ci: PartCreateInfo, provider: CellProvider<SignalClamperCell>) :
    GridCellPart<SignalClamperCell>(ci, provider),
    ComponentDisplay,
    PartWithKnobs,
    ScrewdriverScrollable,
    ScrewdriverInteractable,
    ConnectedPart {

    override val knobMap = KnobMap(this::onKnobMapChanged)

    val knobMin = knobMap.addKnobBB(
        this,
        FlwModels.SIGNAL_CLAMPER_MIN_KNOB,
        Vector3d.unitY,
        "knob_min",
        5.85, 0.8, 5.675,
        0.325, 0.45, 0.325
    ).configure {
        setLimits(-MAX_SIGNAL, MAX_SIGNAL)
        makeInteractable("waila.eln2.clamper_min")
    }

    val knobMax = knobMap.addKnobBB(
        this,
        FlwModels.SIGNAL_CLAMPER_MAX_KNOB,
        Vector3d.unitY,
        "knob_max",
        5.85, 0.8, 6.175,
        0.325, 0.45, 0.325
    ).configure {
        setLimits(-MAX_SIGNAL, MAX_SIGNAL)
        makeInteractable("waila.eln2.clamper_max")
    }

    val inputTerminal = defineCellBoxTerminalBB(
        7.8438, 0.775, 4.1437,
        0.3, 0.55, 0.3,
        highlightColor = MyColor.RED,
        categories = listOf(GridMaterialCategory.SignalGrid)
    )

    val outputTerminal = defineCellBoxTerminalBB(
        7.8437, 0.775, 11.5812,
        0.3, 0.55, 0.3,
        highlightColor = MyColor.BLUE,
        categories = listOf(GridMaterialCategory.SignalGrid)
    )

    @ClientOnly
    private var renderStateImpl = ConnectedPartRenderStateImpl.createIfApplicable(this)

    @ClientOnly
    override val connectedRenderState: ConnectedPartRenderState get() = renderStateImpl!!

    override fun getSyncTag() = ConnectedPart.pack(this)

    @ClientOnly
    override fun handleSyncTag(tag: CompoundTag) {
        renderStateImpl!!.set(getConnectedPartsFromTag(tag))
    }

    override fun onConnectivityChanged() = this.setSyncDirty()

    override fun createVisual(ctx: MultipartVisualizationContext) = ConnectedPartWithKnobsVisual(
        ctx, this, FlwModels.SIGNAL_CLAMPER_BODY, Eln2Signal.SIGNAL_CLAMPER_MODELS
    )

    override fun onCellAcquired() {
        knobMap.loadChanges {
            knobMin.rotation = cell.clampMin
            knobMax.rotation = cell.clampMax
        }
    }

    private fun onKnobMapChanged() {
        if(!placement.level.isClientSide) {
            if(hasCell) {
                cell.clampMin = knobMin.rotation
                cell.clampMax = knobMax.rotation
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

    override fun submitDisplay(builder: ComponentDisplayList) {
        builder.signalInput(cell.clamper.inputResistor.potential)
        builder.signalOutput(cell.clamper.signalSource.signal)
        builder.translateQuantityRow<Potential>("clamper_min", Quantity(cell.clampMin, VOLT))
        builder.translateQuantityRow<Potential>("clamper_max", Quantity(cell.clampMax, VOLT))
    }
}

//#endregion

/**
 * PID controller with 3 terminals: main input, reference, and output.
 * When the reference terminal is unconnected, the main input is treated as the error directly.
 * When both are connected, the error is computed as reference minus main input.
 * Anti-windup: the integral is frozen when the output saturates to [-[MAX_SIGNAL], +[MAX_SIGNAL]].
 * */
class SignalPidElectricalObject(
    cell: SignalPidCell,
    val inputMap: MonopoleMap,
    val referenceMap: MonopoleMap,
    val outputMap: MonopoleMap
) : ElectricalObject<SignalPidCell>(cell) {
    val inputResistor = Resistor().also { it.resistance = SIGNAL_COMPARE_RESISTANCE }
    val referenceResistor = Resistor().also { it.resistance = SIGNAL_COMPARE_RESISTANCE }
    val signalSource = SignalSource()

    private var referenceConnected = false

    override fun clear() {
        super.clear()
        referenceConnected = false
    }

    override fun offerPolar(remote: ElectricalObject<*>): ElectricalPin? {
        if(inputMap.evaluates(cell, remote.cell)) {
            return inputResistor.positive
        }

        if(referenceMap.evaluates(cell, remote.cell)) {
            referenceConnected = true
            return referenceResistor.positive
        }

        if(outputMap.evaluates(cell, remote.cell)) {
            return signalSource.offerOutput()
        }

        return null
    }

    override fun offerTerminal(gc: GridConnectionCell, m0: GridConnectionCell.NodeInfo): ElectricalPin? {
        return when(m0.terminal) {
            cell.inputTerminal -> inputResistor.positive
            cell.referenceTerminal -> {
                referenceConnected = true
                referenceResistor.positive
            }
            cell.outputTerminal -> signalSource.offerOutput()
            else -> null
        }
    }

    override fun addComponents(circuit: ElectricalComponentSet) {
        circuit.add(inputResistor)
        circuit.add(referenceResistor)
        circuit.add(signalSource)
    }

    override fun build(map: ElectricalConnectivityMap) {
        super.build(map)
        map.ground(inputResistor.negative)
        map.ground(referenceResistor.negative)
        signalSource.build(map)
    }

    override fun subscribe(subscribers: SubscriberCollection<SimulationPhase>) {
        subscribers.addPost(this::tick)
    }

    fun tick(dt: Double, phase: SimulationPhase) {
        val error = if(referenceConnected) {
            referenceResistor.potential - inputResistor.potential
        } else {
            inputResistor.potential
        }

        val proportional = cell.kP * error

        val derivative = if(dt > 0.0) {
            (error - cell.lastError) / dt
        } else {
            0.0
        }

        val rawOutput = proportional + cell.kI * cell.integral + cell.kD * derivative

        val output = rawOutput.coerceIn(-MAX_SIGNAL, MAX_SIGNAL)

        val saturated = !rawOutput.approxEq(output, 1e-6)

        val freezeIntegral = saturated &&
            ((rawOutput > 0 && error > 0) || (rawOutput < 0 && error < 0))

        if(!freezeIntegral) {
            cell.integral += error * dt
        }

        cell.lastError = error

        signalSource.signal = output
    }
}

class SignalPidCell(
    ci: CellCreateInfo,
    inputMap: MonopoleMap,
    referenceMap: MonopoleMap,
    outputMap: MonopoleMap,
    val inputTerminal: Int = 0,
    val referenceTerminal: Int = 1,
    val outputTerminal: Int = 2
) : Cell(ci), SidedElectrical<SignalPidCell> {

    companion object {
        private const val KP = "kP"
        private const val KI = "kI"
        private const val KD = "kD"
        private const val INTEGRAL = "integral"
        private const val LAST_ERROR = "lastError"
    }

    var kP = 1.0
        set(value) {
            field = value.coerceIn(0.0, 10.0)
            setChanged()
        }

    var kI = 0.0
        set(value) {
            field = value.coerceIn(0.0, 10.0)
            setChanged()
        }

    var kD = 0.0
        set(value) {
            field = value.coerceIn(0.0, 10.0)
            setChanged()
        }

    var integral = 0.0

    var lastError = 0.0

    @SimObject
    val pid = SignalPidElectricalObject(this, inputMap, referenceMap, outputMap)

    @Node
    val grid = GridNode(this)

    override fun getElectricalSizeOnSide(side: Base6Direction3d, targetCell: Cell): ElectricalSize? {
        if(pid.inputMap.evaluates(this, targetCell) ||
            pid.referenceMap.evaluates(this, targetCell) ||
            pid.outputMap.evaluates(this, targetCell)) {
            return ElectricalSize.Signal
        }

        return null
    }

    override fun saveCellData() = CompoundTag().also {
        it.putDouble(KP, kP)
        it.putDouble(KI, kI)
        it.putDouble(KD, kD)
        it.putDouble(INTEGRAL, integral)
        it.putDouble(LAST_ERROR, lastError)
    }

    override fun loadCellData(tag: CompoundTag) {
        kP = tag.getDouble(KP)
        kI = tag.getDouble(KI)
        kD = tag.getDouble(KD)
        integral = tag.getDouble(INTEGRAL)
        lastError = tag.getDouble(LAST_ERROR)
    }

    fun submitDisplay(builder: ComponentDisplayList) {
        builder.translateRow("pid_kP", String.format("%.3f", kP))
        builder.translateRow("pid_kI", String.format("%.3f", kI))
        builder.translateRow("pid_kD", String.format("%.3f", kD))
        builder.signalInput(pid.inputResistor.potential)
        builder.signalInput(pid.referenceResistor.potential)
        builder.signalOutput(pid.signalSource.signal)
    }
}

class SignalPidPart(ci: PartCreateInfo, provider: CellProvider<SignalPidCell>) :
    GridCellPart<SignalPidCell>(ci, provider),
    ComponentDisplay,
    PartWithKnobs,
    ScrewdriverScrollable,
    ScrewdriverInteractable,
    ConnectedPart
{
    override val knobMap = KnobMap(this::onKnobMapChanged)

    val knobKP = knobMap.addKnobBB(
        this,
        FlwModels.SIGNAL_PID_KP_KNOB,
        Vector3d.unitY,
        "knob_kP",
        5.85, 0.8, 5.675,
        0.325, 0.45, 0.325
    ).configure {
        setLimits(0.0, 10.0)
        makeInteractable("waila.eln2.pid_kP")
    }

    val knobKI = knobMap.addKnobBB(
        this,
        FlwModels.SIGNAL_PID_KI_KNOB,
        Vector3d.unitY,
        "knob_kI",
        5.85, 0.8, 6.175,
        0.325, 0.45, 0.325
    ).configure {
        setLimits(0.0, 10.0)
        makeInteractable("waila.eln2.pid_kI")
    }

    val knobKD = knobMap.addKnobBB(
        this,
        FlwModels.SIGNAL_PID_KD_KNOB,
        Vector3d.unitY,
        "knob_kD",
        5.85, 0.8, 6.675,
        0.325, 0.45, 0.325
    ).configure {
        setLimits(0.0, 10.0)
        makeInteractable("waila.eln2.pid_kD")
    }

    val inputTerminal = defineCellBoxTerminalBB(
        10.9, 0.775, 7.85,
        0.3, 0.55, 0.3,
        highlightColor = MyColor.RED,
        categories = listOf(GridMaterialCategory.SignalGrid)
    )

    val referenceTerminal = defineCellBoxTerminalBB(
        4.7875, 0.775, 7.85,
        0.3, 0.55, 0.3,
        highlightColor = MyColor.BLUE,
        categories = listOf(GridMaterialCategory.SignalGrid)
    )

    val outputTerminal = defineCellBoxTerminalBB(
        7.8438, 0.775, 4.1437,
        0.3, 0.55, 0.3,
        highlightColor = MyColor.GREEN,
        categories = listOf(GridMaterialCategory.SignalGrid)
    )

    @ClientOnly
    private var renderStateImpl = ConnectedPartRenderStateImpl.createIfApplicable(this)

    @ClientOnly
    override val connectedRenderState: ConnectedPartRenderState get() = renderStateImpl!!

    override fun getSyncTag() = ConnectedPart.pack(this)

    @ClientOnly
    override fun handleSyncTag(tag: CompoundTag) {
        renderStateImpl!!.set(getConnectedPartsFromTag(tag))
    }

    override fun onConnectivityChanged() = this.setSyncDirty()

    override fun createVisual(ctx: MultipartVisualizationContext) = ConnectedPartWithKnobsVisual(
        ctx, this, FlwModels.SIGNAL_PID_BODY, Eln2Signal.SIGNAL_PID_MODELS
    )

    override fun onCellAcquired() {
        knobMap.loadChanges {
            knobKP.rotation = cell.kP
            knobKI.rotation = cell.kI
            knobKD.rotation = cell.kD
        }
    }

    private fun onKnobMapChanged() {
        if(!placement.level.isClientSide) {
            if(hasCell) {
                cell.kP = knobKP.rotation
                cell.kI = knobKI.rotation
                cell.kD = knobKD.rotation
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

    override fun submitDisplay(builder: ComponentDisplayList) {
        builder.debugInIDE {
            "kP: ${knobKP.rotation.rounded()}, kI: ${knobKI.rotation.rounded()}, kD: ${knobKD.rotation.rounded()}"
        }
        cell.submitDisplay(builder)
    }
}
