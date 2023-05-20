@file:Suppress("NonAsciiCharacters")

package org.eln2.mc.common.content

import com.mojang.blaze3d.vertex.PoseStack
import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.Connection
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextComponent
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.*
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.*
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.items.CapabilityItemHandler
import net.minecraftforge.items.ItemStackHandler
import net.minecraftforge.items.SlotItemHandler
import org.ageseries.libage.sim.Material
import org.ageseries.libage.sim.thermal.*
import org.eln2.mc.Eln2
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.client.render.renderColored
import org.eln2.mc.client.render.renderTextured
import org.eln2.mc.common.blocks.foundation.CellBlock
import org.eln2.mc.common.blocks.foundation.CellBlockEntity
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.SubscriberPhase
import org.eln2.mc.common.cells.foundation.objects.SimulationObjectSet
import org.eln2.mc.common.events.AtomicUpdate
import org.eln2.mc.common.events.EventScheduler
import org.eln2.mc.extensions.GuiExtensions.addPlayerGrid
import org.eln2.mc.extensions.LevelExtensions.addParticle
import org.eln2.mc.extensions.LevelExtensions.constructMenu
import org.eln2.mc.extensions.LevelExtensions.playLocalSound
import org.eln2.mc.extensions.LibAgeExtensions.add
import org.eln2.mc.extensions.LibAgeExtensions.connect
import org.eln2.mc.extensions.LibAgeExtensions.remove
import org.eln2.mc.extensions.NbtExtensions.getThermalMassMapped
import org.eln2.mc.extensions.NbtExtensions.putThermalMassMapped
import org.eln2.mc.extensions.ThermalExtensions.subStep
import org.eln2.mc.extensions.Vec3Extensions.plus
import org.eln2.mc.extensions.Vec3Extensions.toVec3
import org.eln2.mc.integration.waila.TooltipBuilder
import org.eln2.mc.mathematics.*
import org.eln2.mc.mathematics.map
import org.eln2.mc.mathematics.mapNormalizedDoubleShort
import org.eln2.mc.mathematics.unmapNormalizedDoubleShort
import org.eln2.mc.mathematics.vec4fOne
import org.eln2.mc.sim.ThermalBody
import org.eln2.mc.utility.McColors
import java.util.*

data class FurnaceOptions(
    /**
     * Resistance used when the furnace is inactive.
     * */
    var idleResistance: Double,

    /**
     * Resistance used when the furnace is running.
     * */
    var runningResistance: Double,

    /**
     * Temperature needed for smelting bodies to begin smelting.
     * */
    var temperatureThreshold: Double,

    /**
     * This temperature will be held constantly by the furnace while in operation.
     * */
    var targetTemperature: Double,

    /**
     * The surface area of the resistor. This will affect the heat transfer rate.
     * */
    var surfaceArea: Double,

    /**
     * The connection parameters with the smelting body.
     * */
    var connectionParameters: ConnectionParameters){
    fun serializeNbt(): CompoundTag {
        val tag = CompoundTag()

        tag.putDouble(IDLE_RES, idleResistance)
        tag.putDouble(RUNNING_RES, runningResistance)
        tag.putDouble(TEMP_THRESH, temperatureThreshold)
        tag.putDouble(TARGET_TEMP, targetTemperature)
        tag.putDouble(SURFACE_AREA, surfaceArea)

        return tag
    }

    fun deserializeNbt(tag: CompoundTag) {
        idleResistance = tag.getDouble(IDLE_RES)
        runningResistance = tag.getDouble(RUNNING_RES)
        temperatureThreshold = tag.getDouble(TEMP_THRESH)
        targetTemperature = tag.getDouble(TARGET_TEMP)
        surfaceArea = tag.getDouble(SURFACE_AREA)
    }

    companion object{
        private const val IDLE_RES = "idleRes"
        private const val RUNNING_RES = "runningRes"
        private const val TARGET_TEMP = "targetTemp"
        private const val TEMP_THRESH = "tempThresh"
        private const val SURFACE_AREA = "surfaceArea"
    }
}

class FurnaceCell(pos: CellPos, id: ResourceLocation) : CellBase(pos, id) {
    companion object {
        private const val OPTIONS = "options"
        private const val RESISTOR_THERMAL_MASS = "resistorThermalMass"
    }

    fun serializeNbt(): CompoundTag{
        return CompoundTag().also {
            it.put(OPTIONS, options.serializeNbt())
            it.putThermalMassMapped(RESISTOR_THERMAL_MASS, resistorHeatBody.thermalMass)
        }
    }

    fun deserializeNbt(tag: CompoundTag){
        options.deserializeNbt(tag.getCompound(OPTIONS))
        resistorHeatBodyUpdate.setLatest(ThermalBody(tag.getThermalMassMapped(RESISTOR_THERMAL_MASS), options.surfaceArea))
    }

    val options = FurnaceOptions(
        1000000.0,
        100.0,
        600.0,
        800.0,
        1.0,
        ConnectionParameters.DEFAULT)

    private var resistorHeatBody = ThermalBody(ThermalMass(Material.IRON), 0.5)
    private val resistorHeatBodyUpdate = AtomicUpdate<ThermalBody>()

    //private var smeltingHeatBody = HeatBody.iron(10.0)

    // Used to track the body added to the system.
    // We only mutate this ref on our simulation thread.
    private var knownSmeltingBody: ThermalBody? = null

    // Used to hold the target smelting body. Mutated from e.g. the game object.
    // In our simulation thread, we implemented a state machine using this.
    // 1. If the external heating body is null, but our known (in-system) body is not, we remove the know body,
    // and update its reference, to be null
    // 2. If the external heating body is not null, and the known body is not equal to it, we update the body in the system.
    // To update, we first check if the known body is not null. If that is so, we remove it from the simulation. This cleans up the previous body.
    // Then, we set its reference to the new one, and insert it into the simulation, also setting up connections.
    private var externalSmeltingBody: ThermalBody? = null

    fun loadSmeltingBody(body: ThermalBody) {
        externalSmeltingBody = body
    }

    fun unloadSmeltingBody(){
        externalSmeltingBody = null
    }

    private val needsBurn get() = knownSmeltingBody != null

    private val simulator = Simulator().also {
        it.add(resistorHeatBody)
    }
    var isHot: Boolean = false
        private set

    /**
     * Gets the temperature of the latest smelting body. This body has been visited by the update thread;
     * it may be different from the latest body loaded with [loadSmeltingBody]
     * */
    val bodyTemperature: Temperature? get() = knownSmeltingBody?.temperature

    /**
     * Gets the temperature of the resistor's body.
     * */
    val resistorTemperature: Temperature get() = resistorHeatBody.temperature

    override fun createObjectSet(): SimulationObjectSet {
        return SimulationObjectSet(ResistorObject())
    }

    override fun onGraphChanged() {
        graph.subscribers.addPreInstantaneous(this::simulationTick)
    }

    override fun onRemoving() {
        graph.subscribers.removeSubscriber(this::simulationTick)
    }

    /**
     * Sets the resistance to the idle value as per [options] and sets [isHot] to **false**.
     * */
    private fun idle() {
        resistorObject.resistance = options.idleResistance
        isHot = false
    }

    /**
     * Based on [options], applies an on-off signal by toggling between [FurnaceOptions.runningResistance] and [FurnaceOptions.idleResistance] to reach
     * the specified [FurnaceOptions.targetTemperature].
     * */
    private fun applyControlSignal(){
        resistorObject.resistance = if(resistorHeatBody.temperatureK < options.targetTemperature){
            options.runningResistance
        } else{
            options.idleResistance
        }
    }

    private fun updateThermalSimulation(dt: Double){
        simulator.subStep(dt, 10) { _, elapsed ->
            // Add converted energy into the system
            resistorHeatBody.thermalEnergy += resistorObject.power * elapsed
        }
    }

    /**
     * Updates the burn state [isHot] based on the temperature of the [knownSmeltingBody] and the threshold,
     * specified in [options].
     * */
    private fun updateBurnState(){
        // P.S. Known is not mutated outside!
        val knownSmeltingBody = this.knownSmeltingBody

        isHot = if(knownSmeltingBody == null) {
            false
        } else{
            knownSmeltingBody.temperatureK > options.temperatureThreshold
        }
    }

    private fun applyExternalUpdates() {
        resistorHeatBodyUpdate.consume {
            if(resistorHeatBody == it){
                // weird.
                return@consume
            }

            simulator.remove(resistorHeatBody)
            resistorHeatBody = it
            simulator.add(resistorHeatBody)

            // Also refit the connection with the smelting body, if it exists
            // Works because knownSmeltingBody reference is not mutated outside our simulation thread

            if(knownSmeltingBody != null){
                simulator.connect(
                    resistorHeatBody,
                    knownSmeltingBody!!,
                    options.connectionParameters)
            }
        }

        // externalSmeltingBody is mutated outside, so we copy it

        // This state machine basically adds/removes the body from the System

        val external = externalSmeltingBody

        // This can be simplified, but makes more sense to me.

        if(external == null){
            // We want to remove the body from the system, if it is in the system.

            if(knownSmeltingBody != null) {
                simulator.remove(knownSmeltingBody!!)
                knownSmeltingBody = null
            }
        }
        else {
            if(external != knownSmeltingBody){
                // We only want updates if the body we have in the system is not the same as the external one

                if(knownSmeltingBody != null){
                    // Remove old body

                    simulator.remove(knownSmeltingBody!!)
                }

                // Apply one update here.

                knownSmeltingBody = external
                simulator.add(external)
                simulator.connect(external, resistorHeatBody, options.connectionParameters)
            }
        }
    }

    private fun simulationTick(elapsed: Double, phase: SubscriberPhase){
        applyExternalUpdates()

        updateThermalSimulation(elapsed)
        updateBurnState()

        if (!needsBurn) {
            // No bodies are loaded in, we will idle here.

            idle()

            return
        }

        // A body needs heating, so we start updating the resistor values.
        applyControlSignal()
    }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        resistorHeatBody.appendBody(builder, config)
        knownSmeltingBody?.appendBody(builder, config)

        super.appendBody(builder, config)
    }

    private val resistorObject = electricalObject as ResistorObject
}

class FurnaceBlockEntity(pos: BlockPos, state: BlockState) :
    CellBlockEntity(pos, state, Content.FURNACE_BLOCK_ENTITY.get()) {

    companion object {
        const val INPUT_SLOT = 0
        const val OUTPUT_SLOT = 1
        private const val BURN_TIME_TARGET = 40

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

    class InventoryHandler(private val furnaceBlockEntity: FurnaceBlockEntity) : ItemStackHandler(2) {
        override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
            LOGGER.info("Inventory Handler inserts $slot $stack")

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

        /**
         * Checks if the specified item can be smelted.
         * */
        private fun canSmelt(stack: ItemStack): Boolean {
            val recipeManager = furnaceBlockEntity.level!!.recipeManager
            val recipe = recipeManager.getRecipeFor(RecipeType.SMELTING, SimpleContainer(stack), furnaceBlockEntity.level!!)

            return !recipe.isEmpty
        }

        fun clear() {
            super.setStackInSlot(INPUT_SLOT, ItemStack.EMPTY)
            super.setStackInSlot(OUTPUT_SLOT, ItemStack.EMPTY)
        }

        val isEmpty = super.getStackInSlot(INPUT_SLOT).isEmpty && super.getStackInSlot(OUTPUT_SLOT).isEmpty
    }

    class FurnaceData : SimpleContainerData(5) {
        companion object {
            private const val RESISTOR_TEMPERATURE = 0
            private const val RESISTOR_TARGET_TEMPERATURE = 1
            private const val BODY_TEMPERATURE = 2
            private const val BODY_TARGET_TEMPERATURE = 3
            private const val SMELT_PROGRESS = 4
        }

        var resistorTemperature: Int
            get() = this.get(RESISTOR_TEMPERATURE)
            set(value) { this.set(RESISTOR_TEMPERATURE, value) }

        var resistorTargetTemperature: Int
            get() = this.get(RESISTOR_TARGET_TEMPERATURE)
            set(value) { this.set(RESISTOR_TARGET_TEMPERATURE, value) }

        val resistorTemperatureProgress: Double get() =
            (resistorTemperature.toDouble() / resistorTargetTemperature.toDouble())
            .coerceIn(0.0, 1.0).definedOrZero()

        var bodyTemperature: Int
            get() = this.get(BODY_TEMPERATURE)
            set(value) { this.set(BODY_TEMPERATURE, value) }

        var bodyTargetTemperature: Int
            get() = this.get(BODY_TARGET_TEMPERATURE)
            set(value) { this.set(BODY_TARGET_TEMPERATURE, value) }

        val bodyTemperatureProgress: Double get() =
            (bodyTemperature.toDouble() / bodyTargetTemperature.toDouble())
            .coerceIn(0.0, 1.0).definedOrZero()

        var smeltProgress: Double
            get() = unmapNormalizedDoubleShort(this.get(SMELT_PROGRESS))
            set(value) { this.set(SMELT_PROGRESS, mapNormalizedDoubleShort(value))}
    }

    private var burnTime = 0

    val inventoryHandler = InventoryHandler(this)
    private val inventoryHandlerLazy = LazyOptional.of { inventoryHandler }
    private var saveTag: CompoundTag? = null

    val data = FurnaceData()

    /**
     * This is the last tracked value on the client.
     * */
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
        data.smeltProgress = 0.0

        burnTime = 0

        val inputStack = inventoryHandler.getStackInSlot(INPUT_SLOT)

        isBurning = if(!inputStack.isEmpty){
            furnaceCell.loadSmeltingBody(ThermalBody(ThermalMass(Material.IRON, mass = 0.1), 1.0))

            true
        } else{
            furnaceCell.unloadSmeltingBody()

            false
        }
    }

    fun inputChanged() {
        if(!isBurning){
            EventScheduler.scheduleWorkPre(0) {
                if(!isRemoved) {
                    loadBurningItem()
                }
            }
        }
    }

    // TODO: cache recipe

    fun serverTick() {
        if(furnaceCell.isHot != clientBurning) {
            // A sync is needed here.

            clientBurning = furnaceCell.isHot
            level!!.sendBlockUpdated(blockPos, blockState, blockState, Block.UPDATE_CLIENTS)
        }

        if (!isBurning) {
            // Nothing can be smelted.

            return
        }

        // The saved data is always changing while we're smelting.
        setChanged()

        data.resistorTemperature = furnaceCell.resistorTemperature.kelvin.toInt()
        data.resistorTargetTemperature = furnaceCell.options.targetTemperature.toInt()

        data.bodyTemperature = (furnaceCell.bodyTemperature ?: STANDARD_TEMPERATURE).kelvin.toInt()
        data.bodyTargetTemperature = furnaceCell.options.temperatureThreshold.toInt()

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
                    }
                }, {
                    error("Could not smelt")
                })
            } else {
                if(furnaceCell.isHot) {
                    burnTime++
                }

                data.smeltProgress = (burnTime / BURN_TIME_TARGET.toDouble()).coerceIn(0.0, 1.0)
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

class FurnaceMenu constructor(
    pContainerId: Int,
    playerInventory: Inventory,
    handler: ItemStackHandler,
    val containerData: FurnaceBlockEntity.FurnaceData,
    val entity: FurnaceBlockEntity?
) : AbstractContainerMenu(Content.FURNACE_MENU.get(), pContainerId) {
    companion object {
        fun create(id: Int, inventory: Inventory, player: Player, entity: FurnaceBlockEntity): FurnaceMenu {
            return FurnaceMenu(
                id,
                inventory,
                entity.inventoryHandler,
                entity.data,
                entity)
        }
    }

    constructor(pContainerId: Int, playerInventory: Inventory): this(
        pContainerId,
        playerInventory,
        ItemStackHandler(2),
        FurnaceBlockEntity.FurnaceData(),
        null
    )

    private val playerGridStart: Int
    private val playerGridEnd: Int

    init {
        addSlot(SlotItemHandler(handler, FurnaceBlockEntity.INPUT_SLOT, 56, 35))
        addSlot(SlotItemHandler(handler, FurnaceBlockEntity.OUTPUT_SLOT, 116, 35))
        addDataSlots(containerData)

        playerGridStart = 2
        playerGridEnd = playerGridStart + this.addPlayerGrid(playerInventory, this::addSlot)
    }

    override fun stillValid(pPlayer: Player): Boolean {
        return true
    }

    override fun quickMoveStack(pPlayer: Player, pIndex: Int): ItemStack {
        val slot = slots[pIndex]

        if(!slot.hasItem()) {
            return ItemStack.EMPTY
        }

        val stack = slot.item

        if(pIndex == FurnaceBlockEntity.INPUT_SLOT || pIndex == FurnaceBlockEntity.OUTPUT_SLOT) {
            // Quick move from input/output to player

            if (!moveItemStackTo(stack, playerGridStart, playerGridEnd, true)) {
                return ItemStack.EMPTY
            }
        }
        else {
            // Only move into input slot

            if(!moveItemStackTo(stack,
                    FurnaceBlockEntity.INPUT_SLOT,
                    FurnaceBlockEntity.INPUT_SLOT + 1,
                    true)){

                return ItemStack.EMPTY
            }
        }

        slot.setChanged()

        entity?.inputChanged()

        return stack
    }
}

class FurnaceScreen(menu: FurnaceMenu, playerInventory: Inventory, title: Component) : AbstractContainerScreen<FurnaceMenu>(menu, playerInventory, title) {
    companion object {
        private val TEXTURE = Eln2.resource("textures/gui/container/furnace_test.png")
        private val TEX_SIZE = Vector2I(256, 256)
        private val BACKGROUND_UV_SIZE = Vector2I(176, 166)

        private val RESISTOR_INDICATOR_POS = Vector2I(13, 28)
        private val BODY_INDICATOR_POS = Vector2I(27, 28)

        private const val INDICATOR_HEIGHT = 57 - 28
        private const val INDICATOR_WIDTH = 21 - 13
        private val INDICATOR_SIZE = Vector2I(INDICATOR_WIDTH, 2)
        private val INDICATOR_COLOR = McColors.red

        private val PROGRESS_ARROW_POS = Vector2I(79, 34)
        private val PROGRESS_UV_POS = Vector2F(176f, 14f)
        private val PROGRESS_UV_SIZE = Vector2F(16f, 16f)
    }

    private val offset get() = Vector2I(leftPos, topPos)

    private fun renderIndicator(stack: PoseStack, position: Vector2I, progress: Double) {
        val vertical = map(
            progress,
            0.0,
            1.0,
            (position.y + INDICATOR_HEIGHT).toDouble(),
            position.y.toDouble()
        )

        renderColored(stack, INDICATOR_COLOR, Rectangle4F(
            (position.x + leftPos).toFloat(),
            (vertical + topPos).toFloat(),
            INDICATOR_SIZE.toVector2F())
        )
    }

    private fun renderTemperatureIndicators(stack: PoseStack) {
        renderIndicator(stack, RESISTOR_INDICATOR_POS, menu.containerData.resistorTemperatureProgress)
        renderIndicator(stack, BODY_INDICATOR_POS, menu.containerData.bodyTemperatureProgress)
    }

    private fun renderProgressArrow(stack: PoseStack) {
        val uvSize = Vector2F(
            map(
                menu.containerData.smeltProgress.toFloat(),
                0f,
                1f,
                0f,
                PROGRESS_UV_SIZE.x
            ),
            PROGRESS_UV_SIZE.y
        )

        renderTextured(
            texture = TEXTURE,
            poseStack = stack,
            blitOffset = 0,
            color = vec4fOne(),
            position = PROGRESS_ARROW_POS + offset,
            uvSize = uvSize.toVector2I(),
            uvPosition = PROGRESS_UV_POS,
            textureSize = TEX_SIZE)
    }

    override fun render(pPoseStack: PoseStack, pMouseX: Int, pMouseY: Int, pPartialTick: Float) {
        super.render(pPoseStack, pMouseX, pMouseY, pPartialTick)

        renderTemperatureIndicators(pPoseStack)
        renderProgressArrow(pPoseStack)
    }

    override fun renderBg(pPoseStack: PoseStack, pPartialTick: Float, pMouseX: Int, pMouseY: Int) {
        renderTextured(
            texture = TEXTURE,
            poseStack = pPoseStack,
            blitOffset = 0,
            color = vec4fOne(),
            position = Vector2I(leftPos, topPos),
            uvSize = BACKGROUND_UV_SIZE,
            uvPosition = Vector2F.zero(),
            textureSize = TEX_SIZE)
    }
}

class FurnaceBlock : CellBlock() {
    override fun getCellProvider(): ResourceLocation {
        return Content.FURNACE_CELL.id
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

    @Deprecated("Deprecated in Java")
    override fun use(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pPlayer: Player,
        pHand: InteractionHand,
        pHit: BlockHitResult
    ): InteractionResult {
        return pLevel.constructMenu(pPos, pPlayer, { TextComponent("Test") }, FurnaceMenu::create)
    }
}
