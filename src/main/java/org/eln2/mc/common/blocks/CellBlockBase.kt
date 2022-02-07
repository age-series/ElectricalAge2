package org.eln2.mc.common.blocks

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
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
import org.eln2.mc.common.In
import org.eln2.mc.common.Side
import org.eln2.mc.common.cell.CellRegistry

abstract class CellBlockBase : Block(Properties.of(Material.STONE)), EntityBlock {
    override fun setPlacedBy(lvl : Level, pos : BlockPos, blState : BlockState, ent : LivingEntity?, itemStack : ItemStack
    ) {
        val cellEntity = lvl.getBlockEntity(pos)!! as CellTileEntity
        cellEntity.setPlacedBy(lvl, pos, blState, ent, itemStack, CellRegistry.registry.getValue(getCellProvider())!!)
    }

    override fun onBlockExploded(blState: BlockState?, lvl: Level?, pos: BlockPos?, exp: Explosion?) {
        destroy(lvl!!, pos!!)
        super.onBlockExploded(blState, lvl, pos, exp)
    }

    override fun onDestroyedByPlayer(blState: BlockState?, lvl: Level?, pos: BlockPos?, pl: Player?, wh: Boolean, flState: FluidState?
    ): Boolean {
        destroy(lvl!!, pos!!)
        return super.onDestroyedByPlayer(blState, lvl, pos, pl, wh, flState)
    }

    private fun destroy(level: Level, pos: BlockPos){
        if(level.isClientSide){
            return
        }

        val cellEntity = level.getBlockEntity(pos)!! as CellTileEntity
        cellEntity.setDestroyed()
    }

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState): BlockEntity? {
        return CellTileEntity(pPos, pState)
    }

    @In(Side.LogicalServer)
    override fun onNeighborChange(blockState: BlockState?, world: LevelReader?, pos: BlockPos?, neighbor: BlockPos?) {
        if(world!!.isClientSide) {
            return
        }
        val cellEntity = world.getBlockEntity(pos!!)!! as CellTileEntity
        cellEntity.neighbourUpdated(neighbor!!)
    }

    // override this:

    abstract fun getCellProvider() : ResourceLocation
}
