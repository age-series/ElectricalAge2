@file:Suppress("NonAsciiCharacters")

package org.eln2.mc.common.content

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.RegistryAccess
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.SimpleContainerData
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.item.crafting.SmeltingRecipe
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.AbstractFurnaceBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.phys.BlockHitResult
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.items.ItemStackHandler
import org.ageseries.libage.data.*
import org.ageseries.libage.mathematics.geometry.Vector2di
import org.ageseries.libage.mathematics.map
import org.ageseries.libage.sim.ChemicalElement
import org.ageseries.libage.sim.ConnectionParameters
import org.ageseries.libage.sim.Simulator
import org.ageseries.libage.sim.ThermalMass
import org.ageseries.libage.sim.electrical.ElectricalSimulation
import org.eln2.mc.ClientOnly
import org.eln2.mc.MODID
import org.eln2.mc.LOG
import org.eln2.mc.ServerOnly
import org.eln2.mc.client.render.foundation.MyColor
import org.eln2.mc.common.blocks.foundation.UprightHorizontalDirectionCellBlock
import org.eln2.mc.common.blocks.foundation.CellBlockEntity
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.containers.ContainerHelper
import org.eln2.mc.common.containers.MyAbstractContainerScreen
import org.eln2.mc.common.containers.SlotItemHandlerWithPlacePredicate
import org.eln2.mc.common.content.modules.Eln2Processing
import org.eln2.mc.PoleMap
import org.eln2.mc.common.recipes.foundation.INPUT_SLOT
import org.eln2.mc.common.recipes.foundation.OUTPUT_SLOT
import org.eln2.mc.extensions.constructMenuHelper2
import org.eln2.mc.extensions.getQuantity
import org.eln2.mc.extensions.putQuantity
import org.eln2.mc.extensions.recipeExists
import org.eln2.mc.integration.ComponentDisplay
import org.eln2.mc.integration.ComponentDisplayList
import org.eln2.mc.resource
import kotlin.math.abs

data class FurnaceOptions(
    var idleResistance: Quantity<Resistance>,
    var runningResistance: Quantity<Resistance>,
    var temperatureThreshold: Quantity<Temperature>,
    var targetTemperature: Quantity<Temperature>,
    var leakageParameters: ConnectionParameters,
)

class FurnaceCell(ci: CellCreateInfo, override val electricalMap: PoleMap) : Cell(ci), SidedElectricalMapped<FurnaceCell> {
    companion object {
        private const val TEMPERATURE = "temperature"
    }

    override val electricalSize: ElectricalSize
        get() = ElectricalSize.Any

    @SimObject
    val resistor = PolarResistorObject(this, electricalMap)

    override fun saveCellData() = CompoundTag().also {
        it.putQuantity(TEMPERATURE, resistorThermalMass.temperature)
    }

    override fun loadCellData(tag: CompoundTag) {
        resistorThermalMass.temperature = tag.getQuantity(TEMPERATURE)
    }

    // Move to heating element item or something
    val options = FurnaceOptions(
        idleResistance = Quantity(ElectricalSimulation.MAX_RESISTANCE),
        runningResistance = Quantity(100.0),
        temperatureThreshold = Quantity(600.0, CELSIUS),
        targetTemperature = Quantity(800.0, CELSIUS),
        ConnectionParameters.DEFAULT.copy(conductance = ConnectionParameters.DEFAULT.conductance * 0.25)
    )

    // Move to heating element item or something
    var resistorThermalMass = ThermalMass(
        ChemicalElement.Iron.asMaterial,
        mass = Quantity(0.5, KILOGRAM)
    ).also {
        environmentData.loadTemperature(it)
    }

    private val environmentSimulator = Simulator().also {
        it.add(resistorThermalMass)

        environmentData.connect(it, resistorThermalMass)
    }

    val isHot get() = resistorThermalMass.temperature >= options.temperatureThreshold

    /**
     * Set this flag from the game thread to indicate if the furnace is active.
     * If true, and power is available, the furnace will heat up.
     * */
    var isActive = false

    override fun subscribe(subscribers: SubscriberCollection<SimulationPhase>) {
        subscribers.addPre(this::simulationTick)
    }

    private fun simulationTick(elapsed: Double, phase: SimulationPhase) {
        resistorThermalMass.energy += abs(resistor.component.power) * elapsed
        environmentSimulator.step(elapsed)

        resistor.component.updateResistance(
            !if (isActive && resistorThermalMass.temperature < options.targetTemperature) {
                options.runningResistance
            } else {
                options.idleResistance
            }
        )
    }
}

class FurnaceBlockEntity(pos: BlockPos, state: BlockState) : CellBlockEntity<FurnaceCell>(pos, state, Eln2Processing.FURNACE_BLOCK_ENTITY.get()), ComponentDisplay {
    companion object {
        private const val BURN_TIME_TARGET = 40

        private const val INVENTORY = "inventory"
        private const val BURN_TIME = "burnTime"

        fun tick(pLevel: Level?, pPos: BlockPos?, pState: BlockState?, pBlockEntity: BlockEntity?) {
            if (pLevel == null || pBlockEntity == null) {
                LOG.error("level or entity null")
                return
            }

            if (pBlockEntity !is FurnaceBlockEntity) {
                LOG.error("Got $pBlockEntity instead of furnace")
                return
            }

            if (!pLevel.isClientSide) {
                pBlockEntity.serverTick()
            }
        }
    }

    class InventoryHandler(val blockEntity: FurnaceBlockEntity) : ItemStackHandler(2) {
        override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
            if(slot == OUTPUT_SLOT) {
                return stack
            }

            return super.insertItem(slot, stack, simulate)
        }

        fun export(stack: ItemStack) = super.insertItem(OUTPUT_SLOT, stack, false) != stack

        override fun isItemValid(slot: Int, stack: ItemStack): Boolean {
            return if(slot == INPUT_SLOT) {
                blockEntity.level!!.recipeExists(RecipeType.SMELTING, stack)
            }
            else {
                true
            }
        }

        override fun onContentsChanged(slot: Int) {
            blockEntity.setChanged()
        }
    }

    class FurnaceData : SimpleContainerData(3) {
        companion object {
            private const val RESISTOR_TEMPERATURE = 0
            private const val RESISTOR_TARGET_TEMPERATURE = 1
            private const val SMELT_PROGRESS = 2
        }

        var resistorTemperature: Int
            get() = this.get(RESISTOR_TEMPERATURE)
            set(value) { this.set(RESISTOR_TEMPERATURE, value) }

        var resistorTargetTemperature: Int
            get() = this.get(RESISTOR_TARGET_TEMPERATURE)
            set(value) { this.set(RESISTOR_TARGET_TEMPERATURE, value) }

        val resistorTemperatureProgress: Double
            get() = (resistorTemperature.toDouble() / resistorTargetTemperature.toDouble()).coerceIn(0.0, 1.0)

        var smeltProgress: Double
            get() = (this.get(SMELT_PROGRESS) / 16384.0).coerceIn(0.0, 1.0)
            set(value) { this.set(SMELT_PROGRESS, (value * 16384).toInt().coerceIn(0, 16384)) }
    }

    private var operationBurnTime = 0

    val inventoryHandler = InventoryHandler(this)
    val data = FurnaceData()

    override fun <T : Any?> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return LazyOptional.of { inventoryHandler }.cast()
        }

        return super.getCapability(cap, side)
    }

    private fun loadOperation() {
        data.smeltProgress = 0.0
        operationBurnTime = 0

        val inputStack = inventoryHandler.getStackInSlot(INPUT_SLOT)

        cell.isActive = if (inputStack.isEmpty) {
            false
        } else {
            recipe = level!!
                .recipeManager
                .getRecipeFor(RecipeType.SMELTING, SimpleContainer(inputStack), level!!)
                .get()

            true
        }
    }

    private var recipe: SmeltingRecipe? = null

    fun serverTick() {
        data.resistorTemperature = cell.resistorThermalMass.temperature.value.toInt()
        data.resistorTargetTemperature = cell.options.targetTemperature.value.toInt()

        val isHot = cell.isHot

        if (isHot != blockState.getValue(AbstractFurnaceBlock.LIT)) {
            level!!.setBlock(
                blockPos,
                blockState.setValue(AbstractFurnaceBlock.LIT, isHot),
                Block.UPDATE_ALL
            )
        }

        if (!cell.isActive) {
            loadOperation()
            return
        }

        // The saved data is always changing while we're smelting.
        setChanged()

        val inputStack = inventoryHandler.getStackInSlot(INPUT_SLOT)

        if (inputStack.isEmpty) {
            loadOperation()
            return
        }

        if (operationBurnTime >= BURN_TIME_TARGET) {
            val recipe = this.recipe ?: error("Burning without recipe available")

            if (inventoryHandler.export(ItemStack(recipe.getResultItem(RegistryAccess.EMPTY).item, 1))) {
                inventoryHandler.setStackInSlot(INPUT_SLOT, ItemStack(inputStack.item, inputStack.count - 1))
                loadOperation()
            } else {
                LOG.error("Failed to export item $recipe")
            }
        } else {
            if (isHot) {
                operationBurnTime++
            }

            data.smeltProgress = (operationBurnTime / BURN_TIME_TARGET.toDouble()).coerceIn(0.0, 1.0)
        }
    }

    override fun saveAdditional(pTag: CompoundTag) {
        super.saveAdditional(pTag)

        pTag.put(INVENTORY, inventoryHandler.serializeNBT())
        pTag.putInt(BURN_TIME, operationBurnTime)
    }

    override fun load(pTag: CompoundTag) {
        super.load(pTag)

        inventoryHandler.deserializeNBT(pTag.getCompound(INVENTORY))
        operationBurnTime = pTag.getInt(BURN_TIME)
    }

    override fun submitDisplay(builder: ComponentDisplayList) {
        builder.quantityInput(cell.resistor.component.readouts.power)
        builder.quantity(cell.resistorThermalMass.temperature)
        builder.progress(operationBurnTime / BURN_TIME_TARGET.toDouble())
    }
}

class FurnaceMenu(
    pContainerId: Int,
    playerInventory: Inventory,
    handler: ItemStackHandler,
    val containerData: FurnaceBlockEntity.FurnaceData,
    val access: ContainerLevelAccess,
    val level: Level
) : AbstractContainerMenu(Eln2Processing.FURNACE_MENU.get(), pContainerId) {
    @ServerOnly
    constructor(entity: FurnaceBlockEntity, id: Int, inventory: Inventory): this(
        id,
        inventory,
        entity.inventoryHandler,
        entity.data,
        ContainerLevelAccess.create(entity.level!!, entity.blockPos),
        entity.level!!
    )

    @ClientOnly
    constructor(pContainerId: Int, playerInventory: Inventory) : this(
        pContainerId,
        playerInventory,
        ItemStackHandler(2),
        FurnaceBlockEntity.FurnaceData(),
        ContainerLevelAccess.NULL,
        playerInventory.player.level()
    )

    init {
        addSlot(
            SlotItemHandlerWithPlacePredicate(handler, INPUT_SLOT, 56, 35) {
                level.recipeExists(RecipeType.SMELTING, it)
            }
        )

        addSlot(
            SlotItemHandlerWithPlacePredicate(handler, OUTPUT_SLOT, 116, 35) {
                false
            }
        )

        addDataSlots(containerData)

        ContainerHelper.addPlayerGrid(playerInventory, this::addSlot)
    }

    override fun stillValid(pPlayer: Player) = stillValid(access, pPlayer, Eln2Processing.FURNACE_BLOCK.block.get())

    override fun quickMoveStack(pPlayer: Player, pIndex: Int) = ContainerHelper.quickMove(slots, pPlayer, pIndex)
}

class FurnaceScreen(menu: FurnaceMenu, playerInventory: Inventory, title: Component) :
    MyAbstractContainerScreen<FurnaceMenu>(menu, playerInventory, title) {
    companion object {
        private val TEXTURE = resource("textures/gui/container/furnace.png")

        private val INDICATOR_POS = Vector2di(13, 28)

        private const val INDICATOR_HEIGHT = 57 - 28
        private const val INDICATOR_WIDTH = 21 - 13

        private val PROGRESS_ARROW_POS = Vector2di(79, 34)
        private val PROGRESS_UV_POS = Vector2di(176, 14)
        private val PROGRESS_UV_SIZE = Vector2di(24, 16)
    }

    private fun renderIndicator(pGuiGraphics: GuiGraphics) {
        val progress = menu.containerData.resistorTemperatureProgress

        val height = map(
            progress,
            0.0,
            1.0,
            0.0,
            INDICATOR_HEIGHT.toDouble()
        )

        val position = INDICATOR_POS

        val cold = MyColor(100, 32,195,208)
        val hot = MyColor(150, 255, 90, 0)

        pGuiGraphics.fillGradient(
            leftPos + position.x,
            topPos + position.y + INDICATOR_HEIGHT - height.toInt(),
            leftPos + position.x + INDICATOR_WIDTH,
            topPos + position.y + INDICATOR_HEIGHT,
            MyColor.lerp(cold, hot, progress.toFloat()).data,
            cold.data
        )
    }

    private fun renderProgressArrow(pGuiGraphics: GuiGraphics) {
        pGuiGraphics.blit(
            TEXTURE,
            leftPos + PROGRESS_ARROW_POS.x,
            topPos + PROGRESS_ARROW_POS.y,
            PROGRESS_UV_POS.x.toFloat(),
            PROGRESS_UV_POS.y.toFloat(),
            map(
                menu.containerData.smeltProgress.toFloat(),
                0f,
                1f,
                0f,
                PROGRESS_UV_SIZE.x.toFloat()
            ).toInt(),
            PROGRESS_UV_SIZE.y,
            256,
            256
        )
    }

    override fun render(pGuiGraphics: GuiGraphics, pMouseX: Int, pMouseY: Int, pPartialTick: Float) {
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick)
        renderIndicator(pGuiGraphics)
        renderProgressArrow(pGuiGraphics)
    }

    override fun renderBg(pGuiGraphics: GuiGraphics, pPartialTick: Float, pMouseX: Int, pMouseY: Int) {
        blitHelper(pGuiGraphics, TEXTURE)
    }
}

class FurnaceBlock : UprightHorizontalDirectionCellBlock<FurnaceCell>() {
    init {
        registerDefaultState(defaultBlockState().setValue(AbstractFurnaceBlock.LIT, false))
    }

    override fun createBlockStateDefinition(pBuilder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(pBuilder)
        pBuilder.add(AbstractFurnaceBlock.LIT)
    }

    override fun getCellProvider(): CellProvider<FurnaceCell> {
        return Eln2Processing.FURNACE_CELL.get()
    }

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState): BlockEntity {
        return FurnaceBlockEntity(pPos, pState)
    }

    override fun <T : BlockEntity?> getTicker(
        pLevel: Level,
        pState: BlockState,
        pBlockEntityType: BlockEntityType<T>,
    ): BlockEntityTicker<T> {
        return BlockEntityTicker(FurnaceBlockEntity::tick)
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
        return pLevel.constructMenuHelper2(pPos, pPlayer, Component.translatable("menu.$MODID.furnace"), ::FurnaceMenu)
    }

    override fun animateTick(pState: BlockState, pLevel: Level, pPos: BlockPos, pRandom: RandomSource) {
        if(pState.getValue(AbstractFurnaceBlock.LIT)) {
            val d0 = pPos.x.toDouble() + 0.5
            val d1 = pPos.y.toDouble()
            val d2 = pPos.z.toDouble() + 0.5
            if (pRandom.nextDouble() < 0.5) {
                pLevel.playLocalSound(
                    d0,
                    d1,
                    d2,
                    SoundEvents.FURNACE_FIRE_CRACKLE,
                    SoundSource.BLOCKS,
                    1.0f,
                    1.0f,
                    false
                )
            }

            val direction = pState.getValue(AbstractFurnaceBlock.FACING)

            repeat(4) {
                val d4 = pRandom.nextDouble() * 0.6 - 0.3
                val d5 = if (direction.axis === Direction.Axis.X) direction.stepX.toDouble() * 0.52 else d4
                val d6 = pRandom.nextDouble() * 6.0 / 16.0
                val d7 = if (direction.axis === Direction.Axis.Z) direction.stepZ.toDouble() * 0.52 else d4
                pLevel.addParticle(ParticleTypes.SMOKE, d0 + d5, d1 + d6, d2 + d7, 0.0, 0.0, 0.0)
                pLevel.addParticle(ParticleTypes.FLAME, d0 + d5, d1 + d6, d2 + d7, 0.0, 0.0, 0.0)
            }
        }
    }
}
