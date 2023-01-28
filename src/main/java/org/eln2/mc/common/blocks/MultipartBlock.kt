package org.eln2.mc.common.blocks

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Material
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.EntityCollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import org.eln2.mc.Eln2

class MultipartBlock : Block(Properties.of(Material.AIR).noOcclusion()), EntityBlock {
    private val emptyBox = box(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState): BlockEntity {
        return MultipartBlockEntity(pPos, pState)
    }

    override fun skipRendering(pState: BlockState, pAdjacentBlockState: BlockState, pDirection: Direction): Boolean {
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun getCollisionShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext
    ): VoxelShape {
        return getPartShape(pLevel, pPos, pContext)
    }

    @Deprecated("Deprecated in Java")
    override fun getShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext
    ): VoxelShape {

        return getPartShape(pLevel, pPos, pContext)
    }

    @Deprecated("Deprecated in Java")
    override fun getVisualShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext
    ): VoxelShape {
        return getPartShape(pLevel, pPos, pContext)
    }

    private fun getPartShape(pLevel : BlockGetter, pPos: BlockPos, pContext : CollisionContext) : VoxelShape{
        val multipart = pLevel.getBlockEntity(pPos)

        if(multipart == null){
            Eln2.LOGGER.error("Multipart block failed to get entity at $pPos")
            return emptyBox
        }

        if(multipart !is MultipartBlockEntity){
            Eln2.LOGGER.error("Multipart block found other entity type at $pPos")
            return emptyBox
        }

        if(pContext !is EntityCollisionContext){
            Eln2.LOGGER.error("Collision context was not an entity collision context at $pPos")
            return emptyBox
        }

        if(pContext.entity !is Player){
            Eln2.LOGGER.error("Collision context entity was not a player at $pPos")
            return emptyBox
        }

        val pickedPart = multipart.pickPart(pContext.entity as Player)
            ?: return emptyBox

        return pickedPart.shape
    }
}
