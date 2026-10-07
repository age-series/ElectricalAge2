package org.eln2.mc.common.content.processing

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.util.RandomSource
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.items.ItemStackHandler
import net.minecraftforge.registries.RegistryObject
import org.ageseries.libage.data.Power
import org.ageseries.libage.data.Quantity
import org.eln2.mc.ClientOnly
import org.eln2.mc.client.screens.ProgressSupplierMenu
import org.eln2.mc.MODID
import org.eln2.mc.ServerOnly
import org.eln2.mc.common.cells.foundation.CellProvider
import org.eln2.mc.common.containers.ContainerHelper
import org.eln2.mc.common.containers.ProgressContainerData
import org.eln2.mc.common.containers.SlotItemHandlerWithPlacePredicate
import org.eln2.mc.common.containers.SlotItemHandlerWithPlacePredicateAndSkipPickupCheck
import org.eln2.mc.common.content.modules.Eln2Processing
import org.eln2.mc.common.recipes.foundation.DirectSimpleProcessingRecipe
import org.eln2.mc.common.recipes.foundation.INPUT_SLOT
import org.eln2.mc.common.recipes.foundation.OUTPUT_SLOT
import org.eln2.mc.extensions.eln2StillValid
import org.eln2.mc.extensions.recipeExists

class VacuumSealingBlock<C : ProcessingCell>(
    cell: RegistryObject<CellProvider<C>>,
    val power: Quantity<Power>, val speed: Double, val tier: Int
) : ProcessingMachineBlock<C, VacuumSealingBlockEntity<C>>(cell) {
    override fun getTitle(): Component = Component.translatable("menu.$MODID.vacuum_sealing")

    override fun createMenu(pBlockEntity: VacuumSealingBlockEntity<C>, pContainerId: Int, pPlayerInventory: Inventory) = VacuumSealingMenu(pBlockEntity, pContainerId, pPlayerInventory)

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState) = VacuumSealingBlockEntity<C>(pPos, pState)
}

class VacuumSealingBlockEntity<C : ProcessingCell>(pPos: BlockPos, pState: BlockState) :
    SimpleProcessingMachineBlockEntity<C, DirectSimpleProcessingRecipe>(pPos, pState, 2) {
    override val recipe: RecipeType<DirectSimpleProcessingRecipe>
        get() = Eln2Processing.VACUUM_SEALING_RECIPE

    override val sound: RegistryObject<SoundEvent>
        get() = Eln2Processing.VACUUM_SEALING_SOUND

    override val allowProcessingInReverse: Boolean
        get() = false

    override fun loadSettings() {
        val block = blockState.block as VacuumSealingBlock<*>

        setDefaultRecipeOptions(block.speed, block.tier)
        cell.loadPower = block.power
    }
}

class VacuumSealingMenu(
    pContainerId: Int,
    playerInventory: Inventory,
    handler: ItemStackHandler,
    val containerData: ProgressContainerData,
    val access: ContainerLevelAccess,
    val level: Level,
): AbstractContainerMenu(Eln2Processing.VACUUM_SEALING_MENU.get(), pContainerId), ProgressSupplierMenu {
    @ServerOnly
    constructor(entity: VacuumSealingBlockEntity<*>, id: Int, inventory: Inventory) : this(
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
        ProgressContainerData(),
        ContainerLevelAccess.NULL,
        playerInventory.player.level()
    )

    init {
        addSlot(
            SlotItemHandlerWithPlacePredicateAndSkipPickupCheck(handler, INPUT_SLOT, 34, 35) {
                level.recipeExists(Eln2Processing.VACUUM_SEALING_RECIPE, it)
            }
        )

        addSlot(
            SlotItemHandlerWithPlacePredicate(handler, OUTPUT_SLOT, 130, 35) {
                false
            }
        )

        addDataSlots(containerData)

        ContainerHelper.addPlayerGrid(playerInventory, this::addSlot)
    }

    override fun stillValid(pPlayer: Player) = eln2StillValid<VacuumSealingBlock<*>>(access, pPlayer)

    override fun quickMoveStack(pPlayer: Player, pIndex: Int) = ContainerHelper.quickMove(slots, pPlayer, pIndex)

    override fun getProgressForRender() = containerData.progress
}

