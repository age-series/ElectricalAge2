package org.eln2.mc.common.blocks.foundation

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Explosion
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Material
import org.eln2.mc.common.cells.CellRegistry

abstract class CellBlock : HorizontalDirectionalBlock(Properties.of(Material.STONE).noOcclusion()), EntityBlock {
    init {
        @Suppress("LeakingThis")
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH))
    }

    override fun getStateForPlacement(pContext: BlockPlaceContext): BlockState? {
        return super.defaultBlockState().setValue(FACING, pContext.horizontalDirection.opposite.counterClockWise)
    }

    override fun createBlockStateDefinition(pBuilder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(pBuilder)
        pBuilder.add(FACING)
    }

    override fun setPlacedBy(
        level : Level,
        pos : BlockPos,
        blockState : BlockState,
        entity : LivingEntity?,
        itemStack : ItemStack
    ) {
        val cellEntity = level.getBlockEntity(pos)!! as CellBlockEntity
        cellEntity.setPlacedBy(level, pos, blockState, entity, itemStack, CellRegistry.registry.getValue(getCellProvider())?: error("Unable to get cell provider"))
    }


    override fun onBlockExploded(blState: BlockState?, lvl: Level?, pos: BlockPos?, exp: Explosion?) {
        destroy(lvl?: error("Level was null"), pos?: error("Position was null"))
        super.onBlockExploded(blState, lvl, pos, exp)
    }

    override fun onDestroyedByPlayer(
        blState: BlockState?,
        lvl: Level?,
        pos: BlockPos?,
        pl: Player?,
        wh: Boolean,
        flState: FluidState?
    ): Boolean {
        destroy(lvl?: error("Level was null"), pos?: error("Position was null"))
        return super.onDestroyedByPlayer(blState, lvl, pos, pl, wh, flState)
    }

    private fun destroy(level: Level, pos: BlockPos){
        if (!level.isClientSide) {
            val cellEntity = level.getBlockEntity(pos)!! as CellBlockEntity
            cellEntity.setDestroyed()
        }
    }

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState): BlockEntity? {
        return CellBlockEntity(pPos, pState)
    }

    // override this:
    abstract fun getCellProvider() : ResourceLocation
}
