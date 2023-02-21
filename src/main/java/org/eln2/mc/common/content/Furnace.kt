@file:Suppress("NonAsciiCharacters")

package org.eln2.mc.common.content

import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.Connection
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.items.CapabilityItemHandler
import net.minecraftforge.items.ItemStackHandler
import org.eln2.mc.Eln2
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.Mathematics.map
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.blocks.foundation.CellBlock
import org.eln2.mc.common.blocks.foundation.CellBlockEntity
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.HeatBody
import org.eln2.mc.common.cells.foundation.objects.SimulationObjectSet
import org.eln2.mc.common.events.AtomicUpdate
import org.eln2.mc.control.PIDCoefficients
import org.eln2.mc.control.PIDController
import org.eln2.mc.extensions.LevelExtensions.addParticle
import org.eln2.mc.extensions.LevelExtensions.playLocalSound
import org.eln2.mc.extensions.Vec3Extensions.plus
import org.eln2.mc.extensions.Vec3Extensions.toVec3
import org.eln2.mc.integration.waila.TooltipBuilder
import java.util.*

data class HeaterMaterialDescription(
    val specificHeat: Double,
    val r0: Double,
    val t0: Double,
    val alpha: Double
)

data class HeatingElementData(
    val mass: Double,
    val material: HeaterMaterialDescription){
    fun computeResistance(t: Double): Double{
        val r0 = material.r0
        val 𝛼 = material.alpha
        val ΔT = t - material.t0

        return r0 * (1 + 𝛼 * ΔT)
    }
}

interface IFurnaceCellContainer {
    val needsBurn: Boolean
}

class FurnaceCell(pos: CellPos, id: ResourceLocation) : CellBase(pos, id) {
    companion object {
        private const val IDLE_RES = "idleRes"
        private const val MIN_RES = "minRes"
        private const val MAX_RES = "maxRes"
        private const val PID = "pid"
        private const val TARGET_TEMP = "targetTemp"
        private const val TEMP_THRESH = "tempThresh"
        private const val HEAT_BODY = "heatBody"
    }

    fun serializeNbt(): CompoundTag{
        val tag = CompoundTag()

        tag.putDouble(IDLE_RES, idleResistance)
        tag.putDouble(MIN_RES, minResistance)
        tag.putDouble(MAX_RES, maxResistance)
        tag.put(PID, pidCoefficients.serializeNbt())
        tag.putDouble(TARGET_TEMP, targetTemperature)
        tag.putDouble(TEMP_THRESH, temperatureThreshold)
        tag.put(HEAT_BODY, resistorHeatBody.serializeNbt())

        return tag
    }

    fun deserializeNbt(tag: CompoundTag){
        idleResistance = tag.getDouble(IDLE_RES)
        minResistance = tag.getDouble(MIN_RES)
        maxResistance = tag.getDouble(MAX_RES)
        pidCoefficients.deserializeNbt(tag.get(PID) as CompoundTag)
        targetTemperature = tag.getDouble(TARGET_TEMP)
        temperatureThreshold = tag.getDouble(TEMP_THRESH)
        resistorHeatBodyUpdate.setLatest(HeatBody.fromNbt(tag.get(HEAT_BODY) as CompoundTag))
    }

    var idleResistance = 1000000.0
    var temperatureThreshold = 300.0
    var targetTemperature = 500.0

    private val resistorHeatBodyUpdate = AtomicUpdate<HeatBody>()
    private var resistorHeatBody = HeatBody.iron(5.0)

    private val pidCoefficients = PIDCoefficients(
        1.5,
        0.05,
        0.000001)

    var minResistance = 25.0
    var maxResistance = 10000.0

    private val pid = PIDController(pidCoefficients)

    var temperatureLossRate = 1.0

    private val resistorTemperatureUpdate = AtomicUpdate<Double>()
    var resistorTemperature
        get() = resistorHeatBody.temperature
        set(value) { resistorTemperatureUpdate.setLatest(value) }

    val isHot get() = resistorTemperature >= temperatureThreshold

    override fun createObjectSet(): SimulationObjectSet {
        return SimulationObjectSet(ResistorObject())
    }

    override fun onGraphChanged() {
        graph.addSubscriber(this::simulationTick)
    }

    override fun onRemoving() {
        graph.removeSubscriber(this::simulationTick)
    }

    private fun idle() {
        resistorObject.resistance = idleResistance
        pid.unwind()
    }

    private fun updatePlant(dt: Double){
        pid.setPoint = 1.0
        pid.minControl = 0.0
        pid.maxControl = 1.0

        val control = pid.update(resistorTemperature / targetTemperature, dt)

        resistorObject.resistance = map(
            pid.maxControl - control, // Invert because smaller resistance -> more power
            pid.minControl,
            pid.maxControl,
            minResistance,
            maxResistance)
    }

    private fun simulateThermalBody(dt: Double) {
        // Remove dissipated energy from the system
        resistorHeatBody.temperature -= temperatureLossRate * dt

        // Ensure our energy is not negative
        resistorHeatBody.ensureNotNegative()

        // Add converted energy into the system
        resistorHeatBody.thermalEnergy += resistorObject.power * dt
    }

    private fun applyExternalChanges() {
        resistorHeatBodyUpdate.consume { resistorHeatBody = it }
        resistorTemperatureUpdate.consume { resistorHeatBody.temperature = it }
    }

    private fun simulationTick(elapsed: Double){
        applyExternalChanges()

        simulateThermalBody(elapsed)

        if (container == null) {
            idle()

            return
        }

        val user = container as? IFurnaceCellContainer
            ?: error("Container was not furnace user")

        if(user.needsBurn) {
            updatePlant(elapsed)
        } else {
            idle()
        }
    }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        resistorHeatBody.appendBody(builder, config)

        super.appendBody(builder, config)
    }

    private val resistorObject = electricalObject as ResistorObject
}

class FurnaceBlockEntity(pos: BlockPos, state: BlockState) :
    CellBlockEntity(pos, state, BlockRegistry.FURNACE_BLOCK_ENTITY.get()), IFurnaceCellContainer {
    companion object {
        private const val INPUT_SLOT = 0
        private const val OUTPUT_SLOT = 1
        private const val BURN_TIME_TARGET = 20

        private const val FURNACE = "furnace"
        private const val INVENTORY = "inventory"
        private const val BURN_TIME = "burnTime"
        private const val FURNACE_CELL = "furnaceCell"
        private const val BURNING = "burning"

        fun tick(pLevel: Level?, pPos: BlockPos?, pState: BlockState?, pBlockEntity: BlockEntity?) {
            if (pLevel == null || pBlockEntity == null) {
                LOGGER.error("level or entity null")
                return
            }

            if (pBlockEntity !is FurnaceBlockEntity) {
                LOGGER.error("Got $pBlockEntity instead of furnace")
                return
            }

            if (!pLevel.isClientSide) {
                pBlockEntity.serverTick()
            }
        }
    }

    private class InventoryHandler(private val furnaceBlockEntity: FurnaceBlockEntity) : ItemStackHandler(2) {
        override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
            if(slot == INPUT_SLOT){
                return if(canSmelt(stack)){
                    super.insertItem(slot, stack, simulate).also {
                        if(it != stack){
                            furnaceBlockEntity.inputChanged()
                        }
                    }
                } else {
                    stack
                }
            }

            if(slot == OUTPUT_SLOT){
                return stack
            }

            error("Unknown slot $slot")
        }

        fun insertOutput(stack: ItemStack): Boolean{
            return super.insertItem(OUTPUT_SLOT, stack, false) != stack
        }

        override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack {
            if(slot == INPUT_SLOT){
                return ItemStack.EMPTY
            }

            return super.extractItem(slot, amount, simulate)
        }

        /**
         * Checks if the specified item can be smelted.
         * */
        fun canSmelt(stack: ItemStack): Boolean {
            val recipeManager = furnaceBlockEntity.level!!.recipeManager
            val recipe = recipeManager.getRecipeFor(RecipeType.SMELTING, SimpleContainer(stack), furnaceBlockEntity.level!!)

            return !recipe.isEmpty
        }
    }

    private var burnTime = 0

    private val inventoryHandlerLazy = LazyOptional.of { InventoryHandler(this) }

    private var saveTag: CompoundTag? = null

    var clientBurning = false
        private set

    override fun <T : Any?> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        if(cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return inventoryHandlerLazy.cast()
        }

        return super.getCapability(cap, side)
    }

    override var needsBurn: Boolean = false
        private set

    private fun loadSmeltingItem() {
        burnTime = 0
        needsBurn = false

        inventoryHandlerLazy.ifPresent {
            val inputStack = it.getStackInSlot(0)

            if(!inputStack.isEmpty){
                needsBurn = true
            }
        }
    }

    private fun inputChanged() {
        if(!needsBurn){
            loadSmeltingItem()
        }
    }

    // TODO: cache recipe

    fun serverTick() {
        if(furnaceCell.isHot != clientBurning) {
            clientBurning = furnaceCell.isHot
            level!!.sendBlockUpdated(blockPos, blockState, blockState, Block.UPDATE_CLIENTS)
        }

        LOGGER.info("Needs Burn: $needsBurn")

        if (!needsBurn) {
            return
        }

        inventoryHandlerLazy.ifPresent { inventory ->
            val inputStack = inventory.getStackInSlot(INPUT_SLOT)

            LOGGER.info("INP STACK: $inputStack")
            LOGGER.info("OUT STACK: ${inventory.getStackInSlot(OUTPUT_SLOT)}")

            if(inputStack.isEmpty){
                loadSmeltingItem()

                return@ifPresent
            }

            if (burnTime >= BURN_TIME_TARGET) {
                val recipeLazy = level!!
                    .recipeManager
                    .getRecipeFor(RecipeType.SMELTING, SimpleContainer(inputStack), level!!)

                recipeLazy.ifPresentOrElse({ recipe ->
                    if(!inventory.insertOutput(ItemStack(recipe.resultItem.item, 1))){
                        LOGGER.error("Failed to export item")
                    } else {
                        // Done, load next (also remove input item)
                        inventory.setStackInSlot(INPUT_SLOT, ItemStack(inputStack.item, inputStack.count - 1))

                        loadSmeltingItem()

                        LOGGER.info("Export")
                    }
                }, {
                    error("Could not smelt")
                })
            } else {
                Eln2.LOGGER.info("Trying to burn")
                if(furnaceCell.isHot) {
                    burnTime++
                }
            }
        }
    }

    override fun saveAdditional(pTag: CompoundTag) {
        super.saveAdditional(pTag)

        val tag = CompoundTag()

        inventoryHandlerLazy.ifPresent {
            tag.put(INVENTORY, it.serializeNBT())
        }

        tag.putInt(BURN_TIME, burnTime)
        tag.put(FURNACE_CELL, furnaceCell.serializeNbt())

        pTag.put(FURNACE, tag)
    }

    override fun load(pTag: CompoundTag) {
        super.load(pTag)

        saveTag = pTag.get(FURNACE) as? CompoundTag
    }

    override fun setLevel(pLevel: Level) {
        super.setLevel(pLevel)

        if (saveTag == null) {
            return
        }

        inventoryHandlerLazy.ifPresent {
            val inventoryTag = saveTag!!.get(INVENTORY) as? CompoundTag

            if(inventoryTag != null) {
                it.deserializeNBT(inventoryTag)
            }
        }

        // This resets burnTime, so we load it before loading burnTime:
        loadSmeltingItem()

        burnTime = saveTag!!.getInt(BURN_TIME)
        furnaceCell.deserializeNbt(saveTag!!.get(FURNACE_CELL) as CompoundTag)

        // GC reference tracking
        saveTag = null
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? {
        return ClientboundBlockEntityDataPacket.create(this){ entity ->
            if(entity !is FurnaceBlockEntity){
                return@create null
            }

            return@create CompoundTag().also { it.putBoolean(BURNING, furnaceCell.isHot) }
        }
    }

    override fun onDataPacket(net: Connection?, pkt: ClientboundBlockEntityDataPacket?) {
        super.onDataPacket(net, pkt)

        if(pkt == null){
            return
        }

        clientBurning = pkt.tag!!.getBoolean(BURNING)
    }

    private val furnaceCell get() = cell as FurnaceCell
}

class FurnaceBlock : CellBlock() {
    override fun getCellProvider(): ResourceLocation {
        return CellRegistry.FURNACE_CELL.id
    }

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState): BlockEntity {
        return FurnaceBlockEntity(pPos, pState)
    }

    override fun <T : BlockEntity?> getTicker(
        pLevel: Level,
        pState: BlockState,
        pBlockEntityType: BlockEntityType<T>
    ): BlockEntityTicker<T> {
        return BlockEntityTicker(FurnaceBlockEntity::tick)
    }

    override fun animateTick(blockState: BlockState, level: Level, pos: BlockPos, random: Random) {
        val entity = level.getBlockEntity(pos) as? FurnaceBlockEntity
            ?: return

        if(!entity.clientBurning){
            return
        }

        val sidePos = pos.toVec3() + Vec3(0.5, 0.0, 0.5)

        if (random.nextDouble() < 0.1) {
            level.playLocalSound(
                sidePos,
                SoundEvents.FURNACE_FIRE_CRACKLE,
                SoundSource.BLOCKS,
                1.0f,
                1.0f,
                false)
        }

        val facing = blockState.getValue(FACING)
        val axis = facing.axis
        val randomOffset = random.nextDouble() * 0.6 - 0.3

        val randomOffset3 = Vec3(
            if (axis === Direction.Axis.X) facing.stepX.toDouble() * 0.52 else randomOffset,
            random.nextDouble() * 6.0 / 16.0,
            if (axis === Direction.Axis.Z) facing.stepZ.toDouble() * 0.52 else randomOffset)

        val particlePos = sidePos + randomOffset3

        level.addParticle(ParticleTypes.SMOKE, particlePos, 0.0, 0.0, 0.0)
        level.addParticle(ParticleTypes.FLAME, particlePos, 0.0, 0.0, 0.0)
    }
}
