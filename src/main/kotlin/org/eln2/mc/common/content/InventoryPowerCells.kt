@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package org.eln2.mc.common.content

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.capabilities.ICapabilityProvider
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.energy.IEnergyStorage
import net.minecraftforge.items.ItemStackHandler
import org.ageseries.libage.data.*
import org.ageseries.libage.mathematics.approxEq
import org.ageseries.libage.mathematics.geometry.Vector2di
import org.ageseries.libage.mathematics.rounded
import org.ageseries.libage.sim.Pole
import org.ageseries.libage.sim.electrical.ElectricalComponentSet
import org.ageseries.libage.sim.electrical.ElectricalConnectivityMap
import org.ageseries.libage.sim.electrical.ElectricalPin
import org.ageseries.libage.sim.electrical.Capacitor
import org.ageseries.libage.sim.electrical.PowerConsumer
import org.ageseries.libage.sim.electrical.Resistor
import org.eln2.mc.ClientOnly
import org.eln2.mc.Eln2Config
import org.eln2.mc.MODID
import org.eln2.mc.RF_PER_JOULE
import org.eln2.mc.ServerOnly
import org.eln2.mc.PoleMap
import org.eln2.mc.client.screens.ProgressSupplierMenu
import org.eln2.mc.common.blocks.foundation.CellBlockEntity
import org.eln2.mc.common.blocks.foundation.UprightHorizontalDirectionCellBlock
import org.eln2.mc.common.cells.foundation.Cell
import org.eln2.mc.common.cells.foundation.CellCreateInfo
import org.eln2.mc.common.cells.foundation.CellProvider
import org.eln2.mc.common.cells.foundation.ElectricalObject
import org.eln2.mc.common.cells.foundation.ElectricalSize
import org.eln2.mc.common.cells.foundation.PersistentObject
import org.eln2.mc.common.cells.foundation.ServerPhase
import org.eln2.mc.common.cells.foundation.SidedElectricalMapped
import org.eln2.mc.common.cells.foundation.SimulationPhase
import org.eln2.mc.common.cells.foundation.SubscriberCollection
import org.eln2.mc.common.cells.foundation.SimObject
import org.eln2.mc.common.cells.foundation.addPost
import org.eln2.mc.common.cells.foundation.addPre
import org.eln2.mc.common.cells.foundation.addStart
import org.eln2.mc.common.containers.ContainerHelper
import org.eln2.mc.common.containers.ProgressContainerData
import org.eln2.mc.common.containers.SlotItemHandlerWithPlacePredicate
import org.eln2.mc.common.events.Scheduler
import org.eln2.mc.common.content.modules.Eln2InventoryPower
import org.eln2.mc.extensions.constructMenuHelper2
import org.eln2.mc.extensions.loadNbt
import org.eln2.mc.extensions.saveNbt
import kotlin.math.min
// P.S. Some of these methods have explicit unit names and are methods instead of properties to make potential mod integration easier (if they use java).

/**
 * Specification for an inventory power cell.
 *
 * @param energyCapacity Total energy the cell can store.
 * @param maxPowerInput Maximum charge rate.
 * @param maxPowerOutput Maximum discharge rate.
 * @param efficiency Round-trip efficiency factor in [0, 1], applied on both charge and discharge. Charging stores `received * efficiency`; discharging costs `delivered / efficiency` from the buffer.
 * */
data class InventoryPowerCellModel(
    val energyCapacity: Quantity<Energy>,
    val maxPowerInput: Quantity<Power>,
    val maxPowerOutput: Quantity<Power>,
    val efficiency: Double,
)

/**
 * Priority for power distribution among [InventoryPowerConsumer]s.
 * The [PlayerPowerManager] grants power to higher-priority consumers first; lower-priority consumers receive what remains.
 * */
enum class InventoryPowerPriority {
    Low,
    Normal,
    High,
    Critical,
}

/**
 * A power consumer attached to a player. Implementations report their current demand; the [PlayerPowerManager] arbitrates distribution and calls [receivePower] with the granted amount.
 * The consumer talks only to the manager, never to producers directly.
 * */
interface InventoryPowerConsumer {
    /**
     * The priority at which this consumer's demand is evaluated.
     * */
    val priority: InventoryPowerPriority

    /**
     * How much power this consumer wants right now. Returns 0 when idle.
     * The manager multiplies this by the tick dt to get the energy budget request.
     * */
    fun powerDemand(): Quantity<Power>

    /**
     * Called by the manager to deliver granted energy.
     * Returns the amount actually consumed (might be less than [granted] if the consumer's state changed, though typically equals [granted]).
     * */
    fun receivePower(granted: Quantity<Energy>): Quantity<Energy>

    /**
     * Called by [PlayerPowerManager] before distribution begins. Implementations should reset per-tick state (granted energy, demand cache).
     * */
    fun preTick() { }

    /**
     * Called by [PlayerPowerManager] after distribution completes. Implementations should compute and cache their power fraction for this tick.
     * */
    fun postTick(dt: Double) { }
}

/**
 * A power producer attached to a player, queried by the [PlayerPowerManager] during distribution.
 * Implementations are typically inventory items exposing [IEln2EnergyStorage].
 * */
interface InventoryPowerProducer {
    /**
     * The maximum power this producer can output this tick.
     * */
    val maxOutput: Quantity<Power>

    /**
     * The energy currently available in the buffer.
     * */
    val availableEnergy: Quantity<Energy>

    /**
     * Draws up to [request] energy from this producer, respecting [maxOutput] and [availableEnergy].
     * Returns the amount actually drawn.
     * */
    fun draw(request: Quantity<Energy>): Quantity<Energy>
}

/**
 * Extends Forge's [IEnergyStorage] with ELN2 energy view and power metadata.
 * The [IEnergyStorage] int methods convert to/from joules using [RF_PER_JOULE] and enforce power caps via [Scheduler.timeStamp].
 * External mods see rate-capped FE; ELN2 code casts to [IEln2EnergyStorage] to read [maxPowerOutput] and use lossless transfer.
 * Energy stored lives in the [ItemStack] NBT; this interface is a view over it.
 * */
interface IEln2EnergyStorage : IEnergyStorage {
    val model: InventoryPowerCellModel

    /**
     * The current stored energy.
     * */
    val energyJoules: Quantity<Energy>

    /**
     * The maximum stored energy in joules.
     * */
    val capacityJoules: Quantity<Energy>

    /**
     * Maximum charge rate.
     * */
    val maxPowerInput: Quantity<Power>

    /**
     * Maximum discharge rate.
     * */
    val maxPowerOutput: Quantity<Power>

    /**
     * Round-trip efficiency applied on both charge and discharge.
     * */
    val efficiency: Double

    /**
     * Receives up to [max] joules, respecting [maxPowerInput] (per tick), [capacityJoules], and [efficiency]. Returns the amount actually accepted into the buffer (post-efficiency).
     * If [simulate] is true, no state is mutated.
     * */
    fun receiveEnergyJoules(max: Quantity<Energy>, simulate: Boolean): Quantity<Energy>

    /**
     * Extracts up to [max] joules, respecting [maxPowerOutput] (per tick), [energyJoules], and [efficiency]. Returns the amount actually delivered (pre-efficiency cost).
     * If [simulate] is true, no state is mutated.
     * */
    fun extractEnergyJoules(max: Quantity<Energy>, simulate: Boolean): Quantity<Energy>

    companion object {
        const val NBT_ENERGY = "energy"
        const val NBT_TICK_RECEIVED = "tickReceived"
        const val NBT_TICK_EXTRACTED = "tickExtracted"
        const val NBT_RECEIVED_THIS_TICK = "receivedThisTick"
        const val NBT_EXTRACTED_THIS_TICK = "extractedThisTick"
    }
}

/**
 * Base class for inventory power cell items. Stores energy in the stack's NBT tag and exposes [IEln2EnergyStorage] via the Forge [ForgeCapabilities.ENERGY] capability.
 * This is similar to a normal FE battery. Doesn't have a simulation.
 * Mirrors the [org.eln2.mc.common.LightBulbItem] pattern for stack NBT access.
 * */
class PowerCellItem(val powerCellModel: InventoryPowerCellModel, val initialChargeFraction: Double = 0.9) : Item(Properties().stacksTo(1)) {
    fun getEnergy(stack: ItemStack): Quantity<Energy> {
        val tag = stack.tag
            ?: return Quantity(0.0, JOULE)

        return Quantity(tag.getDouble(IEln2EnergyStorage.NBT_ENERGY), JOULE)
    }

    /**
     * Writes the stored energy (joules) to the stack's NBT.
     * */
    fun setEnergy(stack: ItemStack, energy: Quantity<Energy>) {
        val clamped = energy.value.coerceIn(0.0, !powerCellModel.energyCapacity)
        stack.getOrCreateTag().putDouble(IEln2EnergyStorage.NBT_ENERGY, clamped)
    }

    /**
     * Creates a stack pre-charged to [initialChargeFraction] of capacity.
     * */
    fun createStack(count: Int = 1): ItemStack {
        val stack = ItemStack(this, count)
        setEnergy(stack, powerCellModel.energyCapacity * initialChargeFraction)
        return stack
    }

    override fun initCapabilities(stack: ItemStack, nbt: CompoundTag?): ICapabilityProvider {
        return PowerCellCapabilityProvider(stack, this)
    }

    /**
     * Called by the [PlayerPowerManager] to draw energy as a producer.
     * Delegates to the stack's [IEln2EnergyStorage] capability.
     * */
    fun drawEnergy(stack: ItemStack, request: Quantity<Energy>): Quantity<Energy> {
        val storage = getStorage(stack)
            ?: return Quantity(0.0, JOULE)

        return storage.extractEnergyJoules(request, simulate = false)
    }

    /**
     * Called by ELN2 chargers to push energy into the cell.
     * */
    fun chargeEnergy(stack: ItemStack, request: Quantity<Energy>): Quantity<Energy> {
        val storage = getStorage(stack)
            ?: return Quantity(0.0, JOULE)

        return storage.receiveEnergyJoules(request, simulate = false)
    }

    /**
     * Returns the [IEln2EnergyStorage] view for this stack, or null if the stack is empty.
     * */
    fun getStorage(stack: ItemStack): IEln2EnergyStorage? {
        if (stack.isEmpty) {
            return null
        }

        val cap = stack.getCapability(ForgeCapabilities.ENERGY).resolve()
        return if (cap.isPresent) cap.get() as? IEln2EnergyStorage else null
    }

    override fun appendHoverText(
        pStack: ItemStack,
        pLevel: Level?,
        pTooltipComponents: MutableList<Component>,
        pIsAdvanced: TooltipFlag,
    ) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced)

        val energy = getEnergy(pStack)
        val capacity = powerCellModel.energyCapacity
        val charge = !energy / !capacity

        val yellow = ChatFormatting.YELLOW
        val gray = ChatFormatting.GRAY
        val aqua = ChatFormatting.AQUA

        pTooltipComponents.add(
            Component.translatable("tooltip.eln2.power_cell.charge")
                .append(": ")
                .append(Component.literal("${(charge * 100).rounded()}%").withStyle(aqua))
                .withStyle(yellow)
        )

        pTooltipComponents.add(
            Component.translatable("tooltip.eln2.power_cell.capacity")
                .append(": ")
                .append(Component.literal(Eln2Config.clientConfig.classifyWithOverride(capacity)).withStyle(gray))
                .withStyle(yellow)
        )

        pTooltipComponents.add(
            Component.translatable("tooltip.eln2.power_cell.max_in")
                .append(": ")
                .append(Component.literal(Eln2Config.clientConfig.classifyWithOverride(powerCellModel.maxPowerInput)).withStyle(gray))
                .withStyle(yellow)
        )

        pTooltipComponents.add(
            Component.translatable("tooltip.eln2.power_cell.max_out")
                .append(": ")
                .append(Component.literal(Eln2Config.clientConfig.classifyWithOverride(powerCellModel.maxPowerOutput)).withStyle(gray))
                .withStyle(yellow)
        )

        pTooltipComponents.add(
            Component.translatable("tooltip.eln2.power_cell.efficiency")
                .append(": ")
                .append(Component.literal("${(powerCellModel.efficiency * 100).rounded(1)}%").withStyle(gray))
                .withStyle(yellow)
        )
    }
}

/**
 * Finds the highest charge fraction among all [PowerCellItem]s in the given player's inventory.
 * Returns null if the player has no power cells.
 * Used by tools to display a charge bar.
 * */
fun findBestBatteryCharge(player: Player): Double? {
    var bestCharge: Double? = null

    for (pStack in player.inventory.items) {
        if (pStack.isEmpty) {
            continue
        }

        val cell = pStack.item as? PowerCellItem ?: continue
        val energy = cell.getEnergy(pStack)
        val charge = !energy / !cell.powerCellModel.energyCapacity

        if (bestCharge == null || charge > bestCharge) {
            bestCharge = charge
        }
    }

    return bestCharge
}

/**
 * Provides [ForgeCapabilities.ENERGY] (as [IEln2EnergyStorage]) for a [PowerCellItem] stack.
 * The storage reads/writes energy and per-tick accumulators directly from the stack's NBT, so it stays valid across stack copies and NBT serialization.
 * */
private class PowerCellCapabilityProvider(private val stack: ItemStack, private val item: PowerCellItem) : ICapabilityProvider, IEln2EnergyStorage {
    override val model: InventoryPowerCellModel
        get() = item.powerCellModel

    override val energyJoules: Quantity<Energy>
        get() = item.getEnergy(stack)

    override val capacityJoules: Quantity<Energy>
        get() = model.energyCapacity

    override val maxPowerInput: Quantity<Power>
        get() = model.maxPowerInput

    override val maxPowerOutput: Quantity<Power>
        get() = model.maxPowerOutput

    override val efficiency: Double
        get() = model.efficiency

    private fun readTickAccumulator(key: String, tickKey: String): Pair<Long, Double> {
        val tag = stack.tag ?: return Pair(-1L, 0.0)
        val tick = tag.getLong(tickKey)
        val amount = if (tag.contains(key)) tag.getDouble(key) else 0.0
        return Pair(tick, amount)
    }

    private fun writeTickAccumulator(key: String, tickKey: String, tick: Long, amount: Double) {
        val tag = stack.getOrCreateTag()
        tag.putLong(tickKey, tick)
        tag.putDouble(key, amount)
    }

    private fun tickEnergyBudget(
        maxPerTick: Quantity<Power>,
        tickKey: String,
        amountKey: String,
    ): Quantity<Energy> {
        val currentTick = Scheduler.timeStamp
        val (lastTick, usedThisTick) = readTickAccumulator(amountKey, tickKey)
        val effectiveUsed = if (lastTick != currentTick) 0.0 else usedThisTick
        val perTickBudget = (!maxPerTick) / 20.0
        val remainingThisTick = (perTickBudget - effectiveUsed).coerceAtLeast(0.0)
        return Quantity(remainingThisTick, JOULE)
    }

    private fun accumulateTickUsage(amount: Double, tickKey: String, amountKey: String) {
        val currentTick = Scheduler.timeStamp
        val (lastTick, usedThisTick) = readTickAccumulator(amountKey, tickKey)
        val newAmount = if (lastTick != currentTick) amount else usedThisTick + amount
        writeTickAccumulator(amountKey, tickKey, currentTick, newAmount)
    }

    override fun receiveEnergyJoules(max: Quantity<Energy>, simulate: Boolean): Quantity<Energy> {
        val budget = tickEnergyBudget(maxPowerInput, IEln2EnergyStorage.NBT_TICK_RECEIVED, IEln2EnergyStorage.NBT_RECEIVED_THIS_TICK)
        val capacityRoom = (!capacityJoules - !energyJoules).coerceAtLeast(0.0)
        val rawAccepted = min(min(!max, !budget), capacityRoom)

        if (rawAccepted.approxEq(0.0)) {
            return Quantity(0.0, JOULE)
        }

        val stored = rawAccepted * efficiency

        if (!simulate) {
            setEnergyInternal(Quantity(!energyJoules + stored, JOULE))
            accumulateTickUsage(rawAccepted, IEln2EnergyStorage.NBT_TICK_RECEIVED, IEln2EnergyStorage.NBT_RECEIVED_THIS_TICK)
        }

        return Quantity(rawAccepted, JOULE)
    }

    override fun extractEnergyJoules(max: Quantity<Energy>, simulate: Boolean): Quantity<Energy> {
        val budget = tickEnergyBudget(maxPowerOutput, IEln2EnergyStorage.NBT_TICK_EXTRACTED, IEln2EnergyStorage.NBT_EXTRACTED_THIS_TICK)
        val available = !energyJoules
        val rawDelivered = min(min(!max, !budget), available)

        if (rawDelivered.approxEq(0.0)) {
            return Quantity(0.0, JOULE)
        }

        val cost = rawDelivered / efficiency

        if (!simulate) {
            setEnergyInternal(Quantity(!energyJoules - cost, JOULE))
            accumulateTickUsage(rawDelivered, IEln2EnergyStorage.NBT_TICK_EXTRACTED, IEln2EnergyStorage.NBT_EXTRACTED_THIS_TICK)
        }

        return Quantity(rawDelivered, JOULE)
    }

    private fun setEnergyInternal(energy: Quantity<Energy>) {
        item.setEnergy(stack, energy)
    }

    override fun receiveEnergy(maxReceive: Int, simulate: Boolean): Int {
        val joules = Quantity(maxReceive.toDouble() / RF_PER_JOULE, JOULE)
        val accepted = receiveEnergyJoules(joules, simulate)
        return (!accepted * RF_PER_JOULE.toDouble()).toLong().toInt().coerceAtLeast(0)
    }

    override fun extractEnergy(maxExtract: Int, simulate: Boolean): Int {
        val joules = Quantity(maxExtract.toDouble() / RF_PER_JOULE, JOULE)
        val extracted = extractEnergyJoules(joules, simulate)
        return (!extracted * RF_PER_JOULE.toDouble()).toLong().toInt().coerceAtLeast(0)
    }

    override fun getEnergyStored(): Int {
        return (!energyJoules * RF_PER_JOULE.toDouble()).toLong().toInt().coerceIn(0, Int.MAX_VALUE)
    }

    override fun getMaxEnergyStored(): Int {
        return (!capacityJoules * RF_PER_JOULE.toDouble()).toLong().toInt().coerceIn(0, Int.MAX_VALUE)
    }

    override fun canExtract(): Boolean = true

    override fun canReceive(): Boolean = true

    override fun <T> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        if (cap == ForgeCapabilities.ENERGY) {
            return LazyOptional.of { this }.cast()
        }
        return LazyOptional.empty()
    }
}

class PowerCellChargerObject<C : Cell>(cell: C, val map: PoleMap) : ElectricalObject<C>(cell), PersistentObject {
    val inputSeriesResistor = Resistor()
    val inputParallelCapacitor = Capacitor()
    val inputConsumer = PowerConsumer()

    init {
        inputSeriesResistor.resistance = INPUT_SERIES_RESISTANCE
        inputParallelCapacitor.capacitance = INPUT_PARALLEL_CAPACITANCE
        inputConsumer.minEquivalentResistance = INPUT_MIN_EQUIVALENT_RESISTANCE
    }

    override fun addComponents(circuit: ElectricalComponentSet) {
        circuit.add(inputSeriesResistor, inputParallelCapacitor, inputConsumer)
    }

    override fun offerPolar(remote: ElectricalObject<*>): ElectricalPin? =
        when (map.evaluateOrNull(cell, remote.cell)) {
            Pole.Positive -> inputSeriesResistor.positive
            Pole.Negative -> inputParallelCapacitor.negative
            null -> null
        }

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
    }

    override fun subscribe(subscribers: SubscriberCollection<SimulationPhase>) {
        subscribers.addPre(this::tickPre)
        subscribers.addPost(this::tickPost)
    }

    private fun tickPre(dt: Double, phase: SimulationPhase) {
        val chargerCell = cell as? PowerCellChargerCell ?: run {
            inputConsumer.targetPower = 0.0
            return
        }

        inputConsumer.targetPower = chargerCell.powerDemand
    }

    private fun tickPost(dt: Double, phase: SimulationPhase) {
        val inputPower = inputConsumer.power.coerceAtLeast(0.0)
        val energy = inputPower * dt

        val chargerCell = cell as? PowerCellChargerCell ?: return
        chargerCell.pendingChargeEnergy += energy
    }

    override fun saveObjectNbt(): CompoundTag {
        val tag = CompoundTag()
        tag.put(CAPACITOR, inputParallelCapacitor.saveNbt())
        return tag
    }

    override fun loadObjectNbt(tag: CompoundTag) {
        inputParallelCapacitor.loadNbt(tag.getCompound(CAPACITOR))
    }

    companion object {
        private const val CAPACITOR = "capacitor"

        const val INPUT_SERIES_RESISTANCE = 0.05
        const val INPUT_PARALLEL_CAPACITANCE = 0.05
        const val INPUT_MIN_EQUIVALENT_RESISTANCE = 0.5
    }
}

class PowerCellChargerCell(ci: CellCreateInfo, override val electricalMap: PoleMap) : Cell(ci), SidedElectricalMapped<PowerCellChargerCell> {
    override val electricalSize: ElectricalSize
        get() = ElectricalSize.Any

    @SimObject
    val charger = PowerCellChargerObject(this, electricalMap)

    var powerDemand: Double = 0.0

    var pendingChargeEnergy: Double = 0.0

    var chargeFraction: Double = 0.0
        private set

    override fun subscribeServerThread(subscribers: SubscriberCollection<ServerPhase>) {
        subscribers.addStart(this::serverTickStart)
    }

    @ServerOnly
    private fun serverTickStart(dt: Double, phase: ServerPhase) {
        val blockEntity = container as? PowerCellChargerBlockEntity ?: run {
            powerDemand = 0.0
            return
        }

        val stack = blockEntity.inventoryHandler.getStackInSlot(0)

        if (stack.isEmpty) {
            powerDemand = 0.0
            chargeFraction = 0.0
            return
        }

        val storage = stack.getCapability(ForgeCapabilities.ENERGY).resolve()

        if (storage.isEmpty || storage.get() !is IEln2EnergyStorage) {
            powerDemand = 0.0
            chargeFraction = 0.0
            return
        }

        val eln2Storage = storage.get() as IEln2EnergyStorage

        val capacity = !eln2Storage.capacityJoules
        val current = !eln2Storage.energyJoules

        chargeFraction = if (capacity.approxEq(0.0)) 0.0 else (current / capacity).coerceIn(0.0, 1.0)

        if (current.approxEq(capacity)) {
            powerDemand = 0.0
            return
        }

        powerDemand = !eln2Storage.maxPowerInput

        val pending = pendingChargeEnergy

        if (pending.approxEq(0.0)) {
            return
        }

        pendingChargeEnergy = 0.0

        val accepted = eln2Storage.receiveEnergyJoules(Quantity(pending, JOULE), simulate = false)

        val rejected = pending - !accepted

        if (rejected > 0.0) {
            pendingChargeEnergy = rejected
        }

        blockEntity.setChanged()
    }
}

class PowerCellChargerBlock : UprightHorizontalDirectionCellBlock<PowerCellChargerCell>() {
    override fun getCellProvider(): CellProvider<PowerCellChargerCell> {
        return Eln2InventoryPower.POWER_CELL_CHARGER_CELL.get()
    }

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState): BlockEntity {
        return PowerCellChargerBlockEntity(pPos, pState)
    }

    override fun <T : BlockEntity?> getTicker(
        pLevel: Level,
        pState: BlockState,
        pBlockEntityType: BlockEntityType<T>,
    ): BlockEntityTicker<T>? {
        if (pLevel.isClientSide) {
            return null
        }

        return BlockEntityTicker { _, _, _, pBlockEntity ->
            if (pBlockEntity is PowerCellChargerBlockEntity) {
                pBlockEntity.serverTick()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun use(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pPlayer: Player,
        pHand: InteractionHand,
        pHit: BlockHitResult,
    ): InteractionResult {
        if (pHand != InteractionHand.MAIN_HAND) {
            return InteractionResult.FAIL
        }

        return pLevel.constructMenuHelper2(pPos, pPlayer, Component.translatable("menu.$MODID.power_cell_charger"), ::PowerCellChargerMenu)
    }
}

class PowerCellChargerBlockEntity(pos: BlockPos, state: BlockState) : CellBlockEntity<PowerCellChargerCell>(pos, state, Eln2InventoryPower.POWER_CELL_CHARGER_BLOCK_ENTITY.get()) {
    companion object {
        private const val INVENTORY = "inventory"
        private const val SLOT_COUNT = 1

        fun tick(pLevel: Level?, pPos: BlockPos?, pState: BlockState?, pBlockEntity: BlockEntity?) {
            if (pLevel == null || pBlockEntity == null) {
                return
            }

            if (pBlockEntity !is PowerCellChargerBlockEntity) {
                return
            }

            if (!pLevel.isClientSide) {
                pBlockEntity.serverTick()
            }
        }
    }

    class InventoryHandler(val blockEntity: PowerCellChargerBlockEntity) : ItemStackHandler(SLOT_COUNT) {
        override fun isItemValid(slot: Int, stack: ItemStack): Boolean {
            return stack.getCapability(ForgeCapabilities.ENERGY).resolve().let {
                it.isPresent && it.get() is IEln2EnergyStorage
            }
        }

        override fun onContentsChanged(slot: Int) {
            blockEntity.setChanged()
        }
    }

    val inventoryHandler = InventoryHandler(this)
    val inventoryHandlerLazy: LazyOptional<InventoryHandler> = LazyOptional.of { inventoryHandler }

    val cellProgressData = ProgressContainerData()

    override fun <T> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return inventoryHandlerLazy.cast()
        }

        return super.getCapability(cap, side)
    }

    override fun invalidateCaps() {
        super.invalidateCaps()
        inventoryHandlerLazy.invalidate()
    }

    @ServerOnly
    fun serverTick() {
        cellProgressData.progress = cell.chargeFraction.toFloat()
    }

    override fun saveAdditional(pTag: CompoundTag) {
        super.saveAdditional(pTag)
        pTag.put(INVENTORY, inventoryHandler.serializeNBT())
    }

    override fun load(pTag: CompoundTag) {
        super.load(pTag)
        inventoryHandler.deserializeNBT(pTag.getCompound(INVENTORY))
    }
}

class PowerCellChargerMenu(
    pContainerId: Int,
    playerInventory: Inventory,
    handler: ItemStackHandler,
    val containerData: ProgressContainerData,
    val access: ContainerLevelAccess,
    val level: Level,
) : AbstractContainerMenu(Eln2InventoryPower.POWER_CELL_CHARGER_MENU.get(), pContainerId), ProgressSupplierMenu {
    companion object {
        private val SLOT_POS = Vector2di(80, 33)
    }

    @ServerOnly
    constructor(entity: PowerCellChargerBlockEntity, id: Int, inventory: Inventory) : this(
        id,
        inventory,
        entity.inventoryHandler,
        entity.cellProgressData,
        ContainerLevelAccess.create(entity.level!!, entity.blockPos),
        entity.level!!
    )

    @ClientOnly
    constructor(pContainerId: Int, playerInventory: Inventory) : this(
        pContainerId,
        playerInventory,
        ItemStackHandler(1),
        ProgressContainerData(),
        ContainerLevelAccess.NULL,
        playerInventory.player.level()
    )

    init {
        addSlot(
            SlotItemHandlerWithPlacePredicate(handler, 0, SLOT_POS.x, SLOT_POS.y) { stack ->
                stack.getCapability(ForgeCapabilities.ENERGY).resolve().let {
                    it.isPresent && it.get() is IEln2EnergyStorage
                }
            }
        )

        addDataSlots(containerData)

        ContainerHelper.addPlayerGrid(playerInventory, this::addSlot)
    }

    override fun stillValid(pPlayer: Player) =
        stillValid(access, pPlayer, Eln2InventoryPower.POWER_CELL_CHARGER_BLOCK.block.get())

    override fun quickMoveStack(pPlayer: Player, pIndex: Int) =
        ContainerHelper.quickMove(slots, pPlayer, pIndex)

    override fun getProgressForRender() = containerData.progress
}
