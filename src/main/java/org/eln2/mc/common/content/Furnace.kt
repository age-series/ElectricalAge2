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
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.Mathematics.map
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.blocks.foundation.CellBlock
import org.eln2.mc.common.blocks.foundation.CellBlockEntity
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.thermodynamics.HeatBody
import org.eln2.mc.common.cells.foundation.objects.SimulationObjectSet
import org.eln2.mc.common.cells.foundation.thermodynamics.HeatBodySystem
import org.eln2.mc.common.events.AtomicUpdate
import org.eln2.mc.control.PIDCoefficients
import org.eln2.mc.control.PIDController
import org.eln2.mc.extensions.LevelExtensions.addParticle
import org.eln2.mc.extensions.LevelExtensions.playLocalSound
import org.eln2.mc.extensions.Vec3Extensions.plus
import org.eln2.mc.extensions.Vec3Extensions.toVec3
import org.eln2.mc.integration.waila.TooltipBuilder
import java.util.*

data class FurnaceOptions(
    var idleResistance: Double,
    var temperatureThreshold: Double,
    var targetTemperature: Double,
    var surfaceArea: Double,
    var minResistance: Double,
    var maxResistance: Double,
    var temperatureLossRate: Double){
    fun serializeNbt(): CompoundTag {
        val tag = CompoundTag()

        tag.putDouble(IDLE_RES, idleResistance)
        tag.putDouble(TEMP_THRESH, temperatureThreshold)
        tag.putDouble(TARGET_TEMP, targetTemperature)
        tag.putDouble(SURFACE_AREA, surfaceArea)
        tag.putDouble(MIN_RES, minResistance)
        tag.putDouble(MAX_RES, maxResistance)
        tag.putDouble(TEMP_LOSS_RATE, temperatureLossRate)

        return tag
    }

    fun deserializeNbt(tag: CompoundTag) {
        idleResistance = tag.getDouble(IDLE_RES)
        temperatureThreshold = tag.getDouble(TEMP_THRESH)
        targetTemperature = tag.getDouble(TARGET_TEMP)
        surfaceArea = tag.getDouble(SURFACE_AREA)
        minResistance = tag.getDouble(MIN_RES)
        maxResistance = tag.getDouble(MAX_RES)
        temperatureLossRate = tag.getDouble(TEMP_LOSS_RATE)
    }

    companion object{
        private const val IDLE_RES = "idleRes"
        private const val MIN_RES = "minRes"
        private const val MAX_RES = "maxRes"
        private const val TARGET_TEMP = "targetTemp"
        private const val TEMP_THRESH = "tempThresh"
        private const val SURFACE_AREA = "surfaceArea"
        private const val TEMP_LOSS_RATE = "tempLossRate"
    }
}

class FurnaceCell(pos: CellPos, id: ResourceLocation) : CellBase(pos, id) {
    companion object {
        private const val OPTIONS = "options"
        private const val PID = "pid"
        private const val RESISTOR_BODY = "heatBody"
    }

    fun serializeNbt(): CompoundTag{
        val tag = CompoundTag()

        tag.put(OPTIONS, options.serializeNbt())
        tag.put(PID, pidCoefficients.serializeNbt())
        tag.put(RESISTOR_BODY, resistorHeatBody.serializeNbt())

        return tag
    }

    fun deserializeNbt(tag: CompoundTag){
        options.deserializeNbt(tag.get(OPTIONS) as CompoundTag)
        pidCoefficients.deserializeNbt(tag.get(PID) as CompoundTag)
        resistorHeatBodyUpdate.setLatest(HeatBody.fromNbt(tag.get(RESISTOR_BODY) as CompoundTag))
    }

    val options = FurnaceOptions(
        1000000.0,
        300.0,
        500.0,
        1.0,
        25.0,
        10000.0,
        0.05)

    private data class BodyPair(val resistor: HeatBody, val smelting: HeatBody)

    private var resistorHeatBody = HeatBody.iron(1.0)
    private val resistorHeatBodyUpdate = AtomicUpdate<HeatBody>()

    //private var smeltingHeatBody = HeatBody.iron(10.0)

    // Used to track the body added to the system.
    // We only mutate this ref on our simulation thread.
    private var knownSmeltingBody: HeatBody? = null

    // Used to hold the target smelting body. Mutated from e.g. the game object.
    // In our simulation thread, we implemented a state machine using this.
    // 1. If the external heating body is null, but our known (in-system) body is not, we remove the know body,
    // and update its reference, to be null
    // 2. If the external heating body is not null, and the known body is not equal to it, we update the body in the system.
    // To update, we first check if the known body is not null. If that is so, we remove it from the simulation. This cleans up the previous body.
    // Then, we set its reference to the new one, and insert it into the simulation, also setting up connections.
    private var externalSmeltingBody: HeatBody? = null

    fun loadSmeltingBody(body: HeatBody) {
        externalSmeltingBody = body
    }

    fun unloadSmeltingBody(){
        externalSmeltingBody = null
    }

    val needsBurn get() = knownSmeltingBody != null

    private val system = HeatBodySystem().also {
        it.insertBody(resistorHeatBody)
    }

    private val pidCoefficients = PIDCoefficients(
        1.5,
        0.05,
        0.000001)


    // Use normalized control
    private val pid = PIDController(pidCoefficients).also {
        it.setPoint = 1.0
        it.minControl = 0.0
        it.maxControl = 1.0
    }

    var isHot: Boolean = false
        private set

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
        resistorObject.resistance = options.idleResistance
        isHot = false
        pid.unwind()
    }

    private fun applyControlSignal(dt: Double){
        val signal = pid.update(resistorHeatBody.temperature / options.targetTemperature, dt)

        resistorObject.resistance = map(
            pid.maxControl - signal, // Invert because smaller resistance -> more power
            pid.minControl,
            pid.maxControl,
            options.minResistance,
            options.maxResistance)
    }

    private fun simulateThermalResistor(dt: Double){
        // Remove dissipated energy from the system
        resistorHeatBody.temperature -= options.temperatureLossRate * dt

        // Ensure our energy is not negative
        resistorHeatBody.ensureNotNegative()

        // Add converted energy into the system
        resistorHeatBody.thermalEnergy += resistorObject.power * dt
    }

    private fun simulateThermalTransfer(dt: Double){
        system.conduction(dt)
    }

    private fun updateBurnState(){
        // Known is not mutated outside

        if(knownSmeltingBody == null) {
            isHot = false
        }
        else{
            isHot = knownSmeltingBody!!.temperature > options.temperatureThreshold
        }
    }

    private fun runThermalSimulation(dt: Double) {
        simulateThermalResistor(dt)
        simulateThermalTransfer(dt)
        updateBurnState()
    }

    private fun applyExternalUpdates() {
        resistorHeatBodyUpdate.consume {
            if(resistorHeatBody == it){
                // weird.
                return@consume
            }

            system.removeBody(resistorHeatBody)
            resistorHeatBody = it
            system.insertBody(resistorHeatBody)

            // Also refit the connection with the smelting body, if it exists
            // Works because knownSmeltingBody reference is not mutated outside our simulation thread

            if(knownSmeltingBody != null){
                system.makeConnection(resistorHeatBody, knownSmeltingBody!!){
                    options.surfaceArea
                }
            }
        }

        // externalSmeltingBody is mutated outside, so we copy it

        // This state machine basically adds/removes the body from the System

        val external = externalSmeltingBody

        // This can be simplified, but makes more sense to me.

        if(external == null){
            // We want to remove the body from the system, if it is in the system.

            if(knownSmeltingBody != null) {
                system.removeBody(knownSmeltingBody!!)
                knownSmeltingBody = null
            }
        }
        else {
            if(external != knownSmeltingBody){
                // We only want updates if the body we have in the system is not the same as the external one

                if(knownSmeltingBody != null){
                    // Remove old body

                    system.removeBody(knownSmeltingBody!!)
                }

                // Apply one update here.

                knownSmeltingBody = external
                system.insertBody(external)
                system.makeConnection(external, resistorHeatBody) {
                    options.surfaceArea
                }
            }
        }
    }

    private fun simulationTick(elapsed: Double){
        applyExternalUpdates()

        runThermalSimulation(elapsed)

        if (!needsBurn) {
            idle()

            return
        }

        applyControlSignal(elapsed)
    }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        resistorHeatBody.appendBody(builder, config)
        knownSmeltingBody?.appendBody(builder, config)

        super.appendBody(builder, config)
    }

    private val resistorObject = electricalObject as ResistorObject
}

class FurnaceBlockEntity(pos: BlockPos, state: BlockState) :
    CellBlockEntity(pos, state, BlockRegistry.FURNACE_BLOCK_ENTITY.get()) {

    companion object {
        private const val INPUT_SLOT = 0
        private const val OUTPUT_SLOT = 1
        private const val BURN_TIME_TARGET = 100

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

    private var isBurning = false

    private fun loadBurningItem() {
        burnTime = 0

        inventoryHandlerLazy.ifPresent {
            val inputStack = it.getStackInSlot(INPUT_SLOT)

            isBurning = if(!inputStack.isEmpty){
                furnaceCell.loadSmeltingBody(HeatBody.iron(10.0))
                true
            } else{
                furnaceCell.unloadSmeltingBody()
                false
            }
        }
    }

    private fun inputChanged() {
        if(!isBurning){
            loadBurningItem()
        }
    }

    // TODO: cache recipe

    fun serverTick() {
        if(furnaceCell.isHot != clientBurning) {
            clientBurning = furnaceCell.isHot
            level!!.sendBlockUpdated(blockPos, blockState, blockState, Block.UPDATE_CLIENTS)
        }

        if (!isBurning) {
            return
        }

        inventoryHandlerLazy.ifPresent { inventory ->
            val inputStack = inventory.getStackInSlot(INPUT_SLOT)

            if(inputStack.isEmpty){
                loadBurningItem()

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

                        loadBurningItem()

                        LOGGER.info("Export")
                    }
                }, {
                    error("Could not smelt")
                })
            } else {
                LOGGER.info("Trying to burn")
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
        loadBurningItem()

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

        repeat(4){
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
}
