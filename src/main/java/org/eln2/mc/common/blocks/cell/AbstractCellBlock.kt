package org.eln2.mc.common.blocks.cell

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Explosion
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Material
import org.eln2.mc.common.In
import org.eln2.mc.common.Side
import org.eln2.mc.common.blocks.CellBlockEntity
import org.eln2.mc.common.cell.CellRegistry

abstract class AbstractCellBlock : HorizontalDirectionalBlock(Properties.of(Material.STONE)), EntityBlock {
    init {
        @Suppress("LeakingThis")
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH))
    }

    override fun getStateForPlacement(pContext: BlockPlaceContext): BlockState? {
        return super.defaultBlockState().setValue(FACING, pContext.horizontalDirection.opposite)
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
        cellEntity.setPlacedBy(level, pos, blockState, entity, itemStack, CellRegistry.registry.getValue(getCellProvider())?: throw Exception("Unable to get cell provider"))
    }


    override fun onBlockExploded(blState: BlockState?, lvl: Level?, pos: BlockPos?, exp: Explosion?) {
        destroy(lvl?: throw Exception("Level was null"), pos?: throw Exception("Position was null"))
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
        destroy(lvl?: throw Exception("Level was null"), pos?: throw Exception("Position was null"))
        return super.onDestroyedByPlayer(blState, lvl, pos, pl, wh, flState)
    }

    private fun destroy(level: Level, pos: BlockPos){
        if(level.isClientSide){
            return
        }
        val cellEntity = level.getBlockEntity(pos)!! as CellBlockEntity
        cellEntity.setDestroyed()
    }

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState): BlockEntity? {
        return CellBlockEntity(pPos, pState)
    }

    @In(Side.LogicalServer)
    override fun onNeighborChange(blockState: BlockState?, world: LevelReader?, pos: BlockPos?, neighbor: BlockPos?) {
        if(world?.isClientSide?: throw Exception("World was null")) {
            return
        }
        val cellEntity = world.getBlockEntity(pos?: throw Exception("Position was null")) as CellBlockEntity
        cellEntity.neighbourUpdated(neighbor?: throw Exception("Neighbor location is null"))
    }

    // override this:
    abstract fun getCellProvider() : ResourceLocation
}
