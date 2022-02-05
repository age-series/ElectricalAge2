package org.eln2.mc.common.blocks

import com.google.common.base.Converter
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Explosion
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Material
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.eln2.mc.common.In
import org.eln2.mc.common.Side
import org.eln2.mc.common.cell.CellProvider
import org.eln2.mc.common.cell.CellRegistry

abstract class CellBlockBase : Block(Properties.of(Material.STONE)), EntityBlock {
    private val _logger : Logger = LogManager.getLogger()

    override fun setPlacedBy(
        level : Level,
        position : BlockPos,
        blockState : BlockState,
        entity : LivingEntity?,
        itemStack : ItemStack
    ) {
        val cellEntity = level.getBlockEntity(position)!! as CellTileEntity
        cellEntity.setPlacedBy(level, position, blockState, entity, itemStack, CellRegistry.registry.getValue(getCellProvider())!!)
    }

    override fun onBlockExploded(state: BlockState?, world: Level?, pos: BlockPos?, explosion: Explosion?) {
        destroy(world!!, pos!!)
        super.onBlockExploded(state, world, pos, explosion)
    }

    override fun onDestroyedByPlayer(
        state: BlockState?,
        world: Level?,
        pos: BlockPos?,
        player: Player?,
        willHarvest: Boolean,
        fluid: FluidState?
    ): Boolean {
        destroy(world!!, pos!!)
        return super.onDestroyedByPlayer(state, world, pos, player, willHarvest, fluid)
    }

    private fun destroy(level: Level, pos: BlockPos){
        if(level.isClientSide){
            return
        }

        val cellEntity = level.getBlockEntity(pos)!! as CellTileEntity
        cellEntity.setDestroyed();
    }

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState): BlockEntity? {
        return CellTileEntity(pPos, pState)
    }

    @In(Side.LogicalServer)
    override fun onNeighborChange(state: BlockState?, world: LevelReader?, pos: BlockPos?, neighbor: BlockPos?) {
        if(world!!.isClientSide) {
            return
        }
        val cellEntity = world.getBlockEntity(pos!!)!! as CellTileEntity
        cellEntity.neighbourUpdated(neighbor!!)
    }

    // override this:

    abstract fun getCellProvider() : ResourceLocation
}
