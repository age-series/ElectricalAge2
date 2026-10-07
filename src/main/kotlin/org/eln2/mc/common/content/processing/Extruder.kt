package org.eln2.mc.common.content.processing

import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.instance.InstanceTypes
import dev.engine_room.flywheel.lib.instance.TransformedInstance
import dev.engine_room.flywheel.lib.material.Materials
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import net.minecraftforge.items.ItemStackHandler
import net.minecraftforge.items.SlotItemHandler
import net.minecraftforge.registries.RegistryObject
import org.ageseries.libage.data.Quantity
import org.ageseries.libage.data.WATT
import org.ageseries.libage.mathematics.geometry.Vector3d
import org.ageseries.libage.mathematics.map
import org.eln2.mc.ClientOnly
import org.eln2.mc.MODID
import org.eln2.mc.ServerOnly
import org.eln2.mc.client.render.FlwModels
import org.eln2.mc.client.render.foundation.PartialModelHelper
import org.eln2.mc.common.cells.foundation.CellProvider
import org.eln2.mc.common.containers.*
import org.eln2.mc.common.content.modules.Eln2Processing
import org.eln2.mc.common.recipes.foundation.CATALYST_SLOT
import org.eln2.mc.common.recipes.foundation.CatalyzedSimpleProcessingRecipe
import org.eln2.mc.common.recipes.foundation.INPUT_SLOT
import org.eln2.mc.common.recipes.foundation.OUTPUT_SLOT
import org.eln2.mc.extensions.eln2StillValid
import org.eln2.mc.extensions.recipeExists
import org.eln2.mc.extensions.transformFacingBlock
import org.eln2.mc.resource

class ExtruderBlock<C : ProcessingCell>(cell: RegistryObject<CellProvider<C>>) : ProcessingMachineBlock<C, ExtruderBlockEntity<C>>(cell) {
    init {
        registerDefaultState(defaultBlockState().setValue(WITH_DIE, false))
    }

    override fun createBlockStateDefinition(pBuilder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(pBuilder)
        pBuilder.add(WITH_DIE)
    }

    override fun getTitle(): MutableComponent = Component.translatable("menu.$MODID.extruder")

    override fun createMenu(pBlockEntity: ExtruderBlockEntity<C>, pContainerId: Int, pPlayerInventory: Inventory) = ExtruderMenu(pBlockEntity, pContainerId, pPlayerInventory)

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState) = ExtruderBlockEntity<C>(pPos, pState)

    override fun getColliderFor(pState: BlockState, pLevel: BlockGetter, pPos: BlockPos, pContext: CollisionContext): VoxelShape = collider

    companion object {
        val WITH_DIE: BooleanProperty = BooleanProperty.create("has_die")

        private val size = Vector3d(16.0, 11.0, 16.0) / 32.0

        private val collider = Shapes.box(
            0.5 - size.x, 0.5 - size.y, 0.5 - size.z,
            0.5 + size.x, 0.5 + size.y, 0.5 + size.z
        )
    }
}

class ExtruderBlockEntity<C : ProcessingCell>(pPos: BlockPos, pState: BlockState) : SimpleProcessingMachineBlockEntity<C, CatalyzedSimpleProcessingRecipe>(pPos, pState, 3) {
    override val recipe: RecipeType<CatalyzedSimpleProcessingRecipe>
        get() = Eln2Processing.EXTRUDING_RECIPE

    override val inputSlots: IntArray
        get() = intArrayOf(INPUT_SLOT, CATALYST_SLOT)

    override val sound: RegistryObject<SoundEvent>
        get() = Eln2Processing.EXTRUDER_SOUND

    override fun loadSettings() {
        super.loadSettings()

        cell.loadPower = Quantity(120.0, WATT)
        cell.thermalFactor = 0.05
    }

    override fun serverTick() {
        super.serverTick()

        val hasDieItem = !inventoryHandler.getStackInSlot(CATALYST_SLOT).isEmpty

        if(hasDieItem != blockState.getValue(ExtruderBlock.WITH_DIE)) {
            level!!.setBlock(
                blockPos,
                blockState.setValue(ExtruderBlock.WITH_DIE, hasDieItem),
                Block.UPDATE_ALL
            )
        }
    }
}

class ExtruderBlockEntityVisual<C : ProcessingCell, BE : ExtruderBlockEntity<C>>(
    ctx: VisualizationContext,
    blockEntity: BE, partialTick: Float,
    workBox: ProcessingCellType,
    composite: ProcessingMachineCompositeModel
) : ProcessingMachineBlockEntityVisual<C, BE>(ctx, blockEntity, partialTick, workBox, composite) {
    var die: TransformedInstance? = null
    override fun executeFrame(dt: Double, renderState: ProcessingMachineBlockEntity.ClientState, processSpeed: Double) {
        val hasDie = blockState.getValue(ExtruderBlock.WITH_DIE)

        if(die != null && !hasDie) {
            die!!.delete()
            die = null
        }
        else if(die == null && hasDie) {
            die = visualizationContext.instancerProvider()
                .instancer(
                    InstanceTypes.TRANSFORMED,
                    PartialModelHelper.applyMaterial(FlwModels.EXTRUDER_DIE, Materials.CUTOUT_BLOCK)
                )
                .createInstance()
                .transformFacingBlock(visualPos, blockEntity)

            relight(die)
        }
    }

    override fun _delete() {
        super._delete()

        die?.delete()
    }
}

class ExtruderMenu(
    pContainerId: Int,
    playerInventory: Inventory,
    handler: ItemStackHandler,
    val containerData: ProgressContainerData,
    val access: ContainerLevelAccess,
    val level: Level,
    val block: ExtruderBlock<*>?
) : AbstractContainerMenu(Eln2Processing.EXTRUDER_MENU.get(), pContainerId) {
    @ServerOnly
    constructor(entity: ExtruderBlockEntity<*>, id: Int, inventory: Inventory): this(
        id,
        inventory,
        entity.inventoryHandler,
        entity.data,
        ContainerLevelAccess.create(entity.level!!, entity.blockPos),
        entity.level!!,
        entity.blockState.block as ExtruderBlock<*>
    )

    @ClientOnly
    constructor(pContainerId: Int, playerInventory: Inventory) : this(
        pContainerId,
        playerInventory,
        ItemStackHandler(3),
        ProgressContainerData(),
        ContainerLevelAccess.NULL,
        playerInventory.player.level(),
        null

    )

    init {
        addSlot(
            SlotItemHandlerWithPlacePredicateAndSkipPickupCheck(handler, INPUT_SLOT, 33, 35) {
                val container = SimpleContainer(3)

                container.setItem(INPUT_SLOT, it)
                container.setItem(CATALYST_SLOT, handler.getStackInSlot(CATALYST_SLOT))

                level.recipeExists(Eln2Processing.EXTRUDING_RECIPE, container)
            }
        )

        addSlot(
            SlotItemHandlerWithPlacePredicate(handler, OUTPUT_SLOT, 131, 35) {
                false
            }
        )

        addSlot(
            SlotItemHandler(handler, CATALYST_SLOT, 85, 35)
        )

        addDataSlots(containerData)

        ContainerHelper.addPlayerGrid(playerInventory, this::addSlot)
    }

    override fun stillValid(pPlayer: Player) = this.eln2StillValid<ExtruderBlock<*>>(access, pPlayer)
    override fun quickMoveStack(pPlayer: Player, pIndex: Int) = ContainerHelper.quickMove(slots, pPlayer, pIndex)
}

class ExtruderScreen(menu: ExtruderMenu, playerInventory: Inventory, title: Component) : MyAbstractContainerScreen<ExtruderMenu>(menu, playerInventory, title) {
    companion object {
        private val BASE_TEXTURE = resource("textures/gui/container/extruder_base.png")
        private val PROGRESS_TEXTURE = resource("textures/gui/container/extruder_progress.png")
    }

    override fun renderBg(pGuiGraphics: GuiGraphics, pPartialTick: Float, pMouseX: Int, pMouseY: Int) {
        blitHelper(pGuiGraphics, BASE_TEXTURE)

        pGuiGraphics.blit(
            PROGRESS_TEXTURE,
            leftPos, topPos,
            0.0f,
            0.0f,
            map(
                menu.containerData.progress,
                0f,
                1f,
                54.0f,
                129.0f
            ).toInt(),
            256,
            256,
            256
        )
    }
}
