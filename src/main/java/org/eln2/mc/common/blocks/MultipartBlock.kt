package org.eln2.mc.common.blocks

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Material
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.EntityCollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import org.eln2.mc.Eln2
import org.eln2.mc.common.parts.Part

class MultipartBlock : Block(Properties.of(Material.AIR).noOcclusion()), EntityBlock {
    /* This is required.
     * When minecraft tries to break the block, but no part is picked, it returns this empty box.
     * But a completely empty box will cause some minecraft processes to fail when they try to get
     */
    private val epsilon = 0.00001
    private val emptyBox = box(0.0, 0.0, 0.0, epsilon, epsilon, epsilon);

    //#region Block Methods

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState): BlockEntity {
        return MultipartBlockEntity(pPos, pState)
    }

    @Deprecated("Deprecated in Java", ReplaceWith("true"))
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
        return getMultipartShape(pLevel, pPos, pContext)
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

    override fun onDestroyedByPlayer(
        state: BlockState?,
        level: Level?,
        pos: BlockPos?,
        player: Player?,
        willHarvest: Boolean,
        fluid: FluidState?
    ): Boolean {
        if(pos == null){
            Eln2.LOGGER.error("Pos null")
            return false
        }

        if(level == null){
            Eln2.LOGGER.error("Destroy level null at $pos")
            return false
        }

        if(player == null){
            Eln2.LOGGER.error("Destroy player null at $pos")
            return false
        }

        val multipart = level.getBlockEntity(pos) as? MultipartBlockEntity

        if(multipart == null){
            Eln2.LOGGER.error("Multipart null at $pos")
            return false
        }

        val completelyDestroyed = multipart.remove(player, level, pos)

        return if(completelyDestroyed){
            // Only a part was removed, there are other parts in this multipart.

            super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid)
        } else{
            // We don't want to destroy the whole block and entity

            false
        }
    }

    override fun neighborChanged(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pBlock: Block,
        pFromPos: BlockPos,
        pIsMoving: Boolean
    ) {
        val multipart = pLevel.getBlockEntity(pPos) as? MultipartBlockEntity ?: return

        multipart.onNeighborDestroyed(pFromPos)

        super.neighborChanged(pState, pLevel, pPos, pBlock, pFromPos, pIsMoving)
    }

    //#endregion

    private fun getMultipartShape(level : BlockGetter, pos: BlockPos, context: CollisionContext) : VoxelShape{
        val multipart = level.getBlockEntity(pos) as? MultipartBlockEntity ?: return emptyBox

        return multipart.collisionShape
    }

    private fun getPartShape(level : BlockGetter, pos: BlockPos, context: CollisionContext) : VoxelShape{
        val pickedPart = pickPart(level, pos, context)
            ?: return emptyBox

        return pickedPart.shape
    }

    private fun pickPart(level : BlockGetter, pos : BlockPos, context: CollisionContext) : Part?{
        if(context !is EntityCollisionContext){
            Eln2.LOGGER.error("Collision context was not an entity collision context at $pos")
            return null
        }

        if(context.entity !is LivingEntity){
            return null
        }

        return pickPart(level, pos, (context.entity as LivingEntity))
    }

    private fun pickPart(level : BlockGetter, pos : BlockPos, entity : LivingEntity) : Part?{
        val multipart = level.getBlockEntity(pos)

        if(multipart == null){
            Eln2.LOGGER.error("Multipart block failed to get entity at $pos")
            return null
        }

        if(multipart !is MultipartBlockEntity){
            Eln2.LOGGER.error("Multipart block found other entity type at $pos")
            return null
        }

        return multipart.pickPart(entity)
    }

}
