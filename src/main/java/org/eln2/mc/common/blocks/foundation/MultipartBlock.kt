package org.eln2.mc.common.blocks.foundation

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Material
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.EntityCollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import org.eln2.mc.Eln2
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.content.GhostLightBlock
import org.eln2.mc.common.parts.PartRegistry
import org.eln2.mc.common.parts.foundation.Part
import java.util.*

class MultipartBlock : BaseEntityBlock(Properties.of(Material.STONE)
    .noOcclusion()
    .destroyTime(0.2f)
    .lightLevel { it.getValue(GhostLightBlock.brightnessProperty) }) {

    private val epsilon = 0.00001
    private val emptyBox = box(0.0, 0.0, 0.0, epsilon, epsilon, epsilon)

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
        if (pos == null) {
            return false
        }

        if (level == null) {
            return false
        }

        if (player == null) {
            return false
        }

        val multipart = level.getBlockEntity(pos) as? MultipartBlockEntity

        if (multipart == null) {
            Eln2.LOGGER.error("Multipart null at $pos")
            return false
        }

        val removedId =
            multipart.remove(player, level)
                ?: return false

        val item = PartRegistry.getPartItem(removedId)

        if (!player.isCreative) {
            player.inventory.add(ItemStack(item))
        }

        // We want to destroy the multipart only if it is empty
        val multipartIsDestroyed = multipart.isEmpty

        if (multipartIsDestroyed) {
            level.destroyBlock(pos, false)
        }

        return multipartIsDestroyed
    }

    override fun addRunningEffects(state: BlockState?, level: Level?, pos: BlockPos?, entity: Entity?): Boolean {
        return true
    }

    override fun addLandingEffects(
        state1: BlockState?,
        level: ServerLevel?,
        pos: BlockPos?,
        state2: BlockState?,
        entity: LivingEntity?,
        numberOfParticles: Int
    ): Boolean {
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun neighborChanged(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pBlock: Block,
        pFromPos: BlockPos,
        pIsMoving: Boolean
    ) {
        val multipart = pLevel.getBlockEntity(pPos) as? MultipartBlockEntity ?: return

        val completelyDestroyed = multipart.onNeighborDestroyed(pFromPos)

        if (completelyDestroyed) {
            pLevel.destroyBlock(pPos, false)
        }

        super.neighborChanged(pState, pLevel, pPos, pBlock, pFromPos, pIsMoving)
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
        val multipart = pLevel
            .getBlockEntity(pPos) as? MultipartBlockEntity
            ?: return InteractionResult.FAIL

        return multipart.use(pPlayer, pHand)
    }

    //#endregion

    private fun getMultipartShape(level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        val multipart = level.getBlockEntity(pos) as? MultipartBlockEntity ?: return emptyBox

        return multipart.collisionShape
    }

    private fun getPartShape(level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        val pickedPart = pickPart(level, pos, context)
            ?: return emptyBox

        return pickedPart.shape
    }

    private fun pickPart(level: BlockGetter, pos: BlockPos, context: CollisionContext): Part? {
        if (context !is EntityCollisionContext) {
            Eln2.LOGGER.error("Collision context was not an entity collision context at $pos")
            return null
        }

        if (context.entity !is LivingEntity) {
            return null
        }

        return pickPart(level, pos, (context.entity as LivingEntity))
    }

    private fun pickPart(level: BlockGetter, pos: BlockPos, entity: LivingEntity): Part? {
        val multipart = level.getBlockEntity(pos)

        if (multipart == null) {
            Eln2.LOGGER.error("Multipart block failed to get entity at $pos")
            return null
        }

        if (multipart !is MultipartBlockEntity) {
            Eln2.LOGGER.error("Multipart block found other entity type at $pos")
            return null
        }

        return multipart.pickPart(entity)
    }

    override fun <T : BlockEntity?> getTicker(
        pLevel: Level,
        pState: BlockState,
        pBlockEntityType: BlockEntityType<T>
    ): BlockEntityTicker<T>? {

        return createTickerHelper(
            pBlockEntityType,
            BlockRegistry.MULTIPART_BLOCK_ENTITY.get(),
            MultipartBlockEntity.Companion::blockTick
        )
    }

    override fun createBlockStateDefinition(pBuilder: StateDefinition.Builder<Block, BlockState>) {
        pBuilder.add(GhostLightBlock.brightnessProperty)
    }
}
