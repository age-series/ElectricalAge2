package org.eln2.mc.common.content

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.ageseries.libage.data.*
import org.ageseries.libage.mathematics.FramerateIndependentSmoother1d
import org.ageseries.libage.mathematics.approxEq
import org.ageseries.libage.mathematics.map
import org.ageseries.libage.sim.ConnectionParameters
import org.ageseries.libage.sim.ThermalMassDefinition
import org.ageseries.libage.sim.electrical.Capacitor
import org.ageseries.libage.sim.electrical.ElectricalComponentSet
import org.ageseries.libage.sim.electrical.ElectricalConnectivityMap
import org.ageseries.libage.sim.electrical.ElectricalPin
import org.ageseries.libage.sim.electrical.PowerConsumer
import org.ageseries.libage.sim.electrical.PowerSource
import org.ageseries.libage.sim.electrical.Resistor
import org.eln2.mc.*
import org.eln2.mc.mathematics.Base6Direction3d
import org.eln2.mc.client.render.foundation.MyColor
import org.eln2.mc.common.blocks.foundation.GridCellBlockEntity
import org.eln2.mc.common.blocks.foundation.UprightHorizontalDirectionCellBlock
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.content.modules.Eln2PowerDevices
import org.eln2.mc.common.grids.GridConnectionCell
import org.eln2.mc.common.grids.GridMaterialCategory
import org.eln2.mc.common.grids.GridNode
import org.eln2.mc.common.network.serverToClient.ClientSidePacketHandlerBuilder
import org.eln2.mc.common.network.serverToClient.BulkPacketHandlerBlockEntity
import org.eln2.mc.common.network.serverToClient.sendBulkPacket
import org.eln2.mc.common.sounds.foundation.SimpleLoopingBlockEntitySoundInstance
import org.eln2.mc.common.sounds.foundation.SoundInfo
import org.eln2.mc.common.sounds.foundation.SoundInstanceTickEvent
import org.eln2.mc.common.specs.foundation.CellSpec
import org.eln2.mc.common.specs.foundation.SpecCreateInfo
import org.eln2.mc.extensions.loadNbt
import org.eln2.mc.extensions.saveNbt
import org.eln2.mc.integration.ComponentDisplay
import org.eln2.mc.integration.ComponentDisplayList
import kotlin.math.min

/**
 * @param powerRating The maximum power **output** of the device.
 * @param eta The conversion efficiency of the device. `Eout,electrical ≈ Ein,electrical * [eta]`
 * @param energyK Energy buffer capacity multiplier. `Capacity = ([powerRating] / [eta]) * dt * energyK`.
 * This value should always be greater than 1.
 * @param potentialRating The max potential across the device's inputs.
 * This value is used to calculate the initial resistance of the sink resistor, such that the input power is bounded regardless of the source circuit's potential, as long as its open-circuit voltage is, at most, [potentialRating].
 * @param minPotentialRating The minimum output potential. Both the signal-mapped and manual setpoints are clamped to at least this value.
 * @param inputSeriesResistance Impedance in series with the input circuit.
 * @param inputSmoothingCapacitance Capacitance in parallel with the input circuit.
 * @param outputSeriesResistance Impedance in series with the output circuit.
 *
 * */
@Suppress("SpellCheckingInspection")
data class DcToDcConverterModel(
    val powerRating: Quantity<Power>,
    val eta: Double,
    val energyK: Double,
    val potentialRating: Quantity<Potential>,
    val minPotentialRating: Quantity<Potential>,
    val inputSeriesResistance: Quantity<Resistance>,
    val inputSmoothingCapacitance: Quantity<Capacitance>,
    val inputEquivalentResistance: Quantity<Resistance>,
    val outputSeriesResistance: Quantity<Resistance>,
) {
    val bufferCapacity = Quantity((!powerRating / eta) * CellGraph.DT * energyK, JOULE)
}

fun interface RejectedEnergyAcceptor {
    fun accept(energy: Quantity<Energy>)
}
fun interface PowerLoadConsumer {
    fun onPowerLoadChange(power: Double)
}

/**
 * DC-DC converter implemented as a power sink + power source.
 * This can generate 2 sub-solvers.
 *
 * @param signalMap If non-null, exposes a high-impedance signal input. When a signal cable is connected, the measured potential is mapped from [0, [MAX_SIGNAL]] to [0, potentialRating] and overrides [setpointPotential]. If null, signal connectivity is disabled and [setpointPotential] is always used.
 * */
abstract class DcToDcConverterObject<C : Cell>(
    cell: C,
    val model: DcToDcConverterModel,
    val signalMap: MonopoleMap? = null,
    val rejectedEnergyAcceptor: RejectedEnergyAcceptor? = null
) : ElectricalObject<C>(cell), PersistentObject {
    /**
     * The internal energy in the device.
     * This value bounds the maximum output power and the maximum input power.
     * */
    var energyBuffer = 0.0
        private set

    /**
     * Changed externally. This is the target open-circuit potential.
     * */
    var setpointPotential = Quantity(0.0, VOLT)

    val inputSeriesResistor = Resistor()
    val inputParallelCapacitor = Capacitor()
    val inputConsumer = PowerConsumer()

    val outputSource = PowerSource()
    val outputSeriesResistor = Resistor()

    val signalResistor: Resistor? = if(signalMap != null) Resistor().also { it.resistance = SIGNAL_COMPARE_RESISTANCE } else null

    private var signalConnected = false
    // Bypass diode might also be necessary!

    init {
        inputSeriesResistor.resistance = !model.inputSeriesResistance
        inputParallelCapacitor.capacitance = !model.inputSmoothingCapacitance
        inputConsumer.minEquivalentResistance = !model.inputEquivalentResistance

        outputSource.maxPotential = !model.potentialRating

        outputSeriesResistor.resistance = !model.outputSeriesResistance
    }

    override fun clear() {
        super.clear()
        signalConnected = false
    }

    override fun offerPolar(remote: ElectricalObject<*>): ElectricalPin? {
        if(signalMap != null && signalMap.evaluates(cell, remote.cell)) {
            signalConnected = true
            return signalResistor!!.positive
        }

        return null
    }

    override fun addComponents(circuit: ElectricalComponentSet) {
        circuit.add(
            inputSeriesResistor, inputParallelCapacitor, inputConsumer,
            outputSource, outputSeriesResistor
        )

        if(signalResistor != null) {
            circuit.add(signalResistor)
        }
    }

    protected fun offerInputNegative() = inputParallelCapacitor.negative
    protected fun offerInputPositive() = inputSeriesResistor.positive
    protected fun offerOutputNegative() = outputSource.negative
    protected fun offerOutputPositive() = outputSeriesResistor.positive

    override fun build(map: ElectricalConnectivityMap) {
        super.build(map)

        map.join(
            inputSeriesResistor.negative,
            inputParallelCapacitor.positive
        )

        map.join(
            inputParallelCapacitor.positive,
            inputConsumer.positive
        )

        map.join(
            inputParallelCapacitor.negative,
            inputConsumer.negative
        )

        map.join(
            outputSource.positive,
            outputSeriesResistor.negative
        )

        if(signalResistor != null) {
            map.ground(signalResistor.negative)
        }
    }

    override fun subscribe(subscribers: SubscriberCollection<SimulationPhase>) {
        subscribers.addPre(this::tickPre)
        subscribers.addPost(this::tickPost)
    }

    /**
     * Updates the max power input, based on the [energyBuffer].
     * */
    private fun setTargetPowerInput(dt: Double) {
        /**
         * The energy needed to fill up the buffer to maximum capacity:
         * */
        val missingEnergy = (!model.bufferCapacity - energyBuffer).coerceAtLeast(0.0)

        /**
         * Sets the desired input rate:
         * */
        inputConsumer.targetPower = missingEnergy / dt
    }

    /**
     * Updates the max power output, based on the [energyBuffer].
     * */
    private fun setTargetOutput(dt: Double) {
        /**
         * The max energy the buffer can provide (including the efficiency):
         * */
        val maxInstantaneousPower = (energyBuffer / dt) * model.eta

        outputSource.targetPower = min(maxInstantaneousPower, !model.powerRating)

        outputSource.maxPotential = if(signalConnected) {
            val mapped = map(signalResistor!!.potential, 0.0, MAX_SIGNAL, 0.0, !model.potentialRating)
            mapped.coerceIn(!model.minPotentialRating, !model.potentialRating)
        } else {
            (!setpointPotential).coerceAtLeast(!model.minPotentialRating)
        }
    }

    private fun tickPre(dt: Double, phase: SimulationPhase) {
        setTargetPowerInput(dt)
        setTargetOutput(dt)
    }

    /**
     * Calculates the energy absorbed by the consumer and adds it to the buffer.
     * */
    private fun acceptInputEnergy(dt: Double) {
        val inputPower = inputConsumer.power.coerceAtLeast(0.0 /* Specified in the docs: numerical instability */)
        val energy = inputPower * dt

        energyBuffer += energy

        if(energyBuffer > !model.bufferCapacity) {
            energyBuffer = !model.bufferCapacity
            cell.setChanged()
        }
        else {
            cell.setChangedIf(!energy.approxEq(0.0))
        }
    }

    /**
     * Calculates the energy transferred by the source and removes it from the buffer.
     * @return The waste energy due to efficiency.
     * */
    private fun drainDeliveredEnergy(dt: Double) : Double {
        val deliveredEnergy = outputSource.power * dt

        // Inputting power!
        if(deliveredEnergy < 0.0) {
            return -deliveredEnergy // waste it away, diode?
        }

        val consumedFromBuffer = deliveredEnergy / model.eta

        energyBuffer -= consumedFromBuffer

        if(energyBuffer < 0.0) {
            if(energyBuffer < -1e-3) {
                LOG.debug("Outputted more energy ($consumedFromBuffer) than possible which left buffer at $energyBuffer")
            }

            energyBuffer = 0.0
            cell.setChanged()
        }

        cell.setChangedIf(!consumedFromBuffer.approxEq(0.0))

        return (consumedFromBuffer - deliveredEnergy).coerceAtLeast(0.0)
    }

    private fun tickPost(dt: Double, phase: SimulationPhase) {
        acceptInputEnergy(dt)

        var rejectedEnergy = drainDeliveredEnergy(dt)
        rejectedEnergy += inputSeriesResistor.power * dt
        rejectedEnergy += outputSeriesResistor.power * dt

        rejectedEnergyAcceptor?.accept(Quantity(rejectedEnergy, JOULE))
    }

    override fun saveObjectNbt(): CompoundTag {
        val tag = CompoundTag()
        tag.putDouble(ENERGY_BUFFER, energyBuffer)
        tag.put(CAPACITOR, inputParallelCapacitor.saveNbt())
        tag.putDouble(SETPOINT, !setpointPotential)
        return tag
    }

    override fun loadObjectNbt(tag: CompoundTag) {
        energyBuffer = tag.getDouble(ENERGY_BUFFER)
        inputParallelCapacitor.loadNbt(tag.getCompound(CAPACITOR))
        setpointPotential = Quantity(tag.getDouble(SETPOINT))
    }

    companion object {
        private const val ENERGY_BUFFER = "energyBuffer"
        private const val CAPACITOR = "capacitor"
        private const val SETPOINT = "setpoint"
    }
}

class TerminalDcToDcConverterObject<C : Cell>(
    cell: C,
    model: DcToDcConverterModel,
    signalMap: MonopoleMap? = null,
    val inputNegative: Int,
    val inputPositive: Int,
    val outputNegative: Int,
    val outputPositive: Int,
    rejectedEnergyAcceptor: RejectedEnergyAcceptor? = null
) : DcToDcConverterObject<C>(cell, model, signalMap, rejectedEnergyAcceptor) {
    override fun offerTerminal(gc: GridConnectionCell, m0: GridConnectionCell.NodeInfo) = when(m0.terminal) {
        inputNegative -> offerInputNegative()
        inputPositive -> offerInputPositive()
        outputNegative -> offerOutputNegative()
        outputPositive -> offerOutputPositive()
        else -> null
    }
}

class TerminalDcToDcConverterCell(
    ci: CellCreateInfo,
    thermalDef: ThermalMassDefinition,
    leakage: ConnectionParameters,
    model: DcToDcConverterModel,
    val signalMap: MonopoleMap? = null,
    inputNegative: Int = 0,
    inputPositive: Int = 1,
    outputNegative: Int = 2,
    outputPositive: Int = 3
) : Cell(ci), SidedElectrical<TerminalDcToDcConverterCell> {
    override val isExclusivelyGridConnected: Boolean
        get() = false

    @SimObject
    val thermalWire = ThermalWireObject(this, thermalDef(), leakage)

    @SimObject
    val converter = TerminalDcToDcConverterObject(
        this,
        model,
        signalMap,
        inputNegative,
        inputPositive,
        outputNegative,
        outputPositive
    ) { rejectedEnergy ->
        thermalWire.thermalBody.energy += rejectedEnergy
    }.also { it.setpointPotential = Quantity(24.0, VOLT) }

    @Replicator
    fun powerLoadReplicator(consumer: PowerLoadConsumer) = PowerLoadReplicatorBehavior(this, consumer)

    @Node
    val grid = GridNode(this)

    override fun getElectricalSizeOnSide(side: Base6Direction3d, targetCell: Cell): ElectricalSize? {
        if(signalMap != null && signalMap.evaluates(this, targetCell)) {
            return ElectricalSize.Signal
        }

        return null
    }
}

class PowerLoadReplicatorBehavior(
    val cell: TerminalDcToDcConverterCell,
    val consumer: PowerLoadConsumer
) : ReplicatorBehavior {
    var powerEps = 1.0
    private var replicatedPower = -1.0

    override fun subscribe(subscribers: SubscriberCollection<SimulationPhase>) {
        subscribers.addSubscriber(
            SubscriberOptions(5, SimulationPhase.Post),
            this::scan
        )
    }

    private fun scan(dt: Double, subscriberPhase: SimulationPhase) {
        val targetPower = cell.converter.inputConsumer.power.coerceAtLeast(0.0)

        if(!targetPower.approxEq(replicatedPower, powerEps)) {
            replicatedPower = targetPower
            consumer.onPowerLoadChange(targetPower)
        }
    }
}

class DcToDcConverterSpec(ci: SpecCreateInfo) :
    CellSpec<TerminalDcToDcConverterCell>(ci, Eln2PowerDevices.TERMINAL_DC_TO_DC_CONVERTER_CELL_800W.get()),
    ScrewdriverScrollable,
    ScrewdriverInteractable,
    ComponentDisplay
{
    val inputNegative = defineCellBoxTerminalBB(
        3.1, 0.275, 6.425,
        0.4, 0.475, 0.575,
        highlightColor = MyColor.BLUE,
    )

    val inputPositive = defineCellBoxTerminalBB(
        3.1, 0.275, 9.0,
        0.4, 0.475, 0.575,
        highlightColor = MyColor.RED
    )

    val outputNegative = defineCellBoxTerminalBB(
        12.5, 0.275, 9.0,
        0.4, 0.475, 0.575,
        highlightColor = MyColor.BLUE,
    )

    val outputPositive = defineCellBoxTerminalBB(
        12.5, 0.275, 6.425,
        0.4, 0.475, 0.575,
        highlightColor = MyColor.RED,
    )

    override fun scrollScrewdriver(player: ServerPlayer, delta: Double) : Boolean {
        if(!hasCell) {
            return false
        }

        val increment = delta / 10.0
        val newPotential = (!cell.converter.setpointPotential + increment).coerceIn(!cell.converter.model.minPotentialRating, !cell.converter.model.potentialRating)

        cell.converter.setpointPotential = Quantity(newPotential)
        cell.setChanged()

        return true
    }

    @ServerOnly
    override fun applyScrewdriver(screwdriver: ScrewdriverItem, context: UseOnContext, configValue: OptionalDouble) {
        if(!hasCell) {
            return
        }

        if(!configValue.isPresent) {
            context.player!!.sendSystemMessage(Component.translatable("waila.eln2.Potential_setpoint_implicit"))
            return
        }

        val max = !cell.converter.model.potentialRating
        val min = !cell.converter.model.minPotentialRating
        val clamped = configValue.unwrap().coerceIn(min, max)
        cell.converter.setpointPotential = Quantity(clamped)
        cell.setChanged()
        context.player!!.sendSystemMessage(Component.translatable("waila.eln2.Potential_setpoint_implicit").append(": ").append(String.format("%.3f", clamped)))
    }

    override fun submitDisplay(builder: ComponentDisplayList) {
        var inputPower = cell.converter.inputConsumer.readouts.power // Let the negative value exist, for out debug log

        builder.debugInIDE { "Buffer: ${cell.converter.energyBuffer.classifyAs(JOULE)}" }
        builder.debugInIDE { "Charge: ${cell.converter.inputParallelCapacitor.readouts.charge.classify()}" }
        builder.debugInIDE { "Input power: ${inputPower.classify()}" }

        if(cell.converter.outputSource.isInSimulation) {
            builder.debugInIDE {
                "SRC Iter: ${cell.converter.outputSource.simulation.lastPowerSourceIterationCount}, " +
                "SRC Res: ${cell.converter.outputSource.simulation.lastPowerSourceMaxResidual}"
            }
        }

        // Clean up the numerical error for player readout:
        inputPower = max(inputPower, Quantity(0.0, WATT))

        builder.quantity(cell.thermalWire.thermalBody.temperature)
        builder.quantityInput(inputPower)

        if(cell.signalMap != null) {
            builder.signalInput(cell.converter.signalResistor!!.potential)
        }

        builder.quantityOutput(cell.converter.outputSource.readouts.potential)
        builder.quantityOutput(cell.converter.outputSource.readouts.current)
        builder.quantityOutput(cell.converter.outputSource.readouts.power)
        builder.quantitySetpoint(cell.converter.setpointPotential)
    }
}

class PrimitivePowerConverterBlock : UprightHorizontalDirectionCellBlock<TerminalDcToDcConverterCell>() {
    override fun getCellProvider() = Eln2PowerDevices.PRIMITIVE_DC_TO_DC_CONVERTER_CELL_5KW.get()

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState) = PrimitivePowerConverterBlockEntity(pPos, pState)

    override fun <T : BlockEntity?> getTicker(
        pLevel: Level,
        pState: BlockState,
        pBlockEntityType: BlockEntityType<T?>
    ): BlockEntityTicker<T> {
        return BlockEntityTicker { _, _, _, pBlockEntity ->
            if(pBlockEntity is PrimitivePowerConverterBlockEntity) {
                if(pLevel.isClientSide) {
                    pBlockEntity.clientTick()
                }
            }
        }
    }

    @Deprecated("Deprecated in Java", ReplaceWith("true"))
    override fun skipRendering(pState: BlockState, pAdjacentBlockState: BlockState, pDirection: Direction) = true

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getCollisionShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext,
    ): VoxelShape = COLLIDER

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext,
    ): VoxelShape = COLLIDER

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getVisualShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext,
    ): VoxelShape = COLLIDER

    companion object {
        private val COLLIDER = Shapes.box(
            0.0, 0.0, 0.0,
            1.0, 11.5 / 16.0, 1.0
        )
    }
}

class PrimitivePowerConverterBlockEntity(pos: BlockPos, state: BlockState) :
    GridCellBlockEntity<TerminalDcToDcConverterCell>(pos, state, Eln2PowerDevices.PRIMITIVE_DC_TO_DC_CONVERTER_BLOCK_ENTITY.get()),
    ScrewdriverScrollable,
    ScrewdriverInteractable,
    ComponentDisplay,
    BulkPacketHandlerBlockEntity,
    PowerLoadConsumer
{
    override fun createTerminals() {
        val categories = listOf(GridMaterialCategory.PowerGrid)

        defineCellBoxTerminalBB(
            3.7, 8.5, 12.7,
            1.6, 3.0, 1.6,
            highlightColor = MyColor.BLUE,
            categories = categories
        )

        defineCellBoxTerminalBB(
            12.7, 8.5, 12.7,
            1.6, 3.0, 1.6,
            highlightColor = MyColor.RED,
            categories = categories
        )

        defineCellBoxTerminalBB(
            3.7, 8.5, 1.7,
            1.6, 3.0, 1.6,
            highlightColor = MyColor.BLUE,
            categories = categories
        )

        defineCellBoxTerminalBB(
            12.7, 8.5, 1.7,
            1.6, 3.0, 1.6,
            highlightColor = MyColor.RED,
            categories = categories
        )
    }

    override fun scrollScrewdriver(player: ServerPlayer, delta: Double) : Boolean {
        if(!hasCell) {
            return false
        }

        val increment = delta / 10.0
        val newPotential = (!cell.converter.setpointPotential + increment).coerceIn(!cell.converter.model.minPotentialRating, !cell.converter.model.potentialRating)

        cell.converter.setpointPotential = Quantity(newPotential)
        cell.setChanged()

        return true
    }

    @ServerOnly
    override fun applyScrewdriver(screwdriver: ScrewdriverItem, context: UseOnContext, configValue: OptionalDouble) {
        if(!hasCell) {
            return
        }

        if(!configValue.isPresent) {
            context.player!!.sendSystemMessage(Component.translatable("waila.eln2.Potential_setpoint_implicit"))
            return
        }
        val max = !cell.converter.model.potentialRating
        val min = !cell.converter.model.minPotentialRating
        val clamped = configValue.unwrap().coerceIn(min, max)
        cell.converter.setpointPotential = Quantity(clamped)
        cell.setChanged()
        context.player!!.sendSystemMessage(Component.translatable("waila.eln2.Potential_setpoint_implicit").append(": ").append(String.format("%.3f", clamped)))
    }

    override fun submitDisplay(builder: ComponentDisplayList) {
        var inputPower = cell.converter.inputConsumer.readouts.power

        builder.debugInIDE { "Buffer: ${cell.converter.energyBuffer.classifyAs(JOULE)}" }
        builder.debugInIDE { "Charge: ${cell.converter.inputParallelCapacitor.readouts.charge.classify()}" }
        builder.debugInIDE { "Input power: ${inputPower.classify()}" }

        if(cell.converter.outputSource.isInSimulation) {
            builder.debugInIDE {
                "SRC Iter: ${cell.converter.outputSource.simulation.lastPowerSourceIterationCount}, " +
                "SRC Res: ${cell.converter.outputSource.simulation.lastPowerSourceMaxResidual}"
            }
        }

        inputPower = max(inputPower, Quantity(0.0, WATT))

        builder.quantity(cell.thermalWire.thermalBody.temperature)
        builder.quantityInput(inputPower)

        if(cell.signalMap != null) {
            builder.signalInput(cell.converter.signalResistor!!.potential)
        }

        builder.quantityOutput(cell.converter.outputSource.readouts.potential)
        builder.quantityOutput(cell.converter.outputSource.readouts.current)
        builder.quantityOutput(cell.converter.outputSource.readouts.power)
        builder.quantitySetpoint(cell.converter.setpointPotential)
    }

    @ClientOnly
    private var renderState: RenderState? = null

    @ClientOnly
    private class RenderState {
        var nominalPower = 1.0
        var targetPower = 0.0
        val powerInterpolator = FramerateIndependentSmoother1d(0.25)
        var soundInstance: SimpleLoopingBlockEntitySoundInstance<PrimitivePowerConverterBlockEntity>? = null
    }

    override fun setLevel(pLevel: Level) {
        super.setLevel(pLevel)

        if(pLevel.isClientSide) {
            renderState = RenderState()
        }
    }

    @ClientOnly
    override val clientSidePacketHandlerLazy = createClientSideHandler()

    @ClientOnly
    override fun setupPacketsOnClient(handler: ClientSidePacketHandlerBuilder) {
        handler.withHandler<PowerLoadPacket>(PowerLoadPacket::deserialize) {
            renderState!!.targetPower = it.power
        }
    }

    @ServerOnly
    override fun onPowerLoadChange(power: Double) {
        sendBulkPacket(PowerLoadPacket::serialize, PowerLoadPacket(power))
    }

    @ClientOnly
    fun clientTick() {
        val state = renderState ?: return

        if(state.soundInstance == null) {
            state.soundInstance = SimpleLoopingBlockEntitySoundInstance(this, Eln2PowerDevices.POWER_ELECTROMAGNETIC_SOUND.get()).also {
                it.events.registerHandler<SoundInstanceTickEvent> { _ ->
                    state.powerInterpolator.update(state.targetPower)
                    it.soundInfo = SoundInfo.electromagnetic(
                        state.powerInterpolator.value,
                        state.nominalPower
                    )
                }

                it.registerOnAudioManager()
            }
        }
    }

    @ServerOnly
    override fun getUpdateTag(): CompoundTag {
        val tag = super.getUpdateTag()

        if(hasCell) {
            tag.putDouble(NOMINAL_POWER, !cell.converter.model.powerRating)
            sendBulkPacket(PowerLoadPacket::serialize, PowerLoadPacket(cell.converter.inputConsumer.power.coerceAtLeast(0.0)))
        }

        return tag
    }

    @ClientOnly
    override fun handleUpdateTag(tag: CompoundTag?) {
        super.handleUpdateTag(tag)

        if(tag != null && tag.contains(NOMINAL_POWER)) {
            renderState?.nominalPower = tag.getDouble(NOMINAL_POWER)
        }
    }

    private data class PowerLoadPacket(val power: Double) {
        companion object {
            fun serialize(packet: PowerLoadPacket, buffer: FriendlyByteBuf) {
                buffer.writeDouble(packet.power)
            }

            fun deserialize(buffer: FriendlyByteBuf) = PowerLoadPacket(
                buffer.readDouble()
            )
        }
    }

    companion object {
        private const val NOMINAL_POWER = "nominalPower"
    }
}
