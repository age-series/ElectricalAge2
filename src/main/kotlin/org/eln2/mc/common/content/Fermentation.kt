package org.eln2.mc.common.content

import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Material
import net.minecraft.world.phys.BlockHitResult
import org.eln2.mc.data.KILOGRAMS
import org.eln2.mc.data.Quantity
import org.eln2.mc.scientific.*
import org.eln2.mc.scientific.chemistry.*
import org.eln2.mc.ticker

class FermentationBarrelBlock : Block(Properties.of(Material.WOOD)), EntityBlock {
    override fun newBlockEntity(pPos: BlockPos, pState: BlockState) = FermentationBarrelBlockEntity(pPos, pState)

    override fun <T : BlockEntity?> getTicker(
        pLevel: Level,
        pState: BlockState,
        pBlockEntityType: BlockEntityType<T>,
    ): BlockEntityTicker<T>? {
        if (!pLevel.isClientSide) {
            if (pBlockEntityType == Content.FERMENTATION_BARREL_BLOCK_ENTITY.get()) {
                return ticker(FermentationBarrelBlockEntity::serverTick)
            }
        }

        return null
    }

    @Deprecated("Deprecated in Java", ReplaceWith("?"))
    override fun use(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pPlayer: Player,
        pHand: InteractionHand,
        pHit: BlockHitResult,
    ): InteractionResult {
        if (!pLevel.isClientSide) {
            val e = pLevel.getBlockEntity(pPos) as? FermentationBarrelBlockEntity ?: return InteractionResult.FAIL

            return e.use(pPlayer, pHand)
        }

        return InteractionResult.PASS
    }
}

// I tried to implement this using the chemistry API, but we need so much more if we want it to even resemble reality...
// So, we'll skip out for now.
class FermentationBarrelBlockEntity(
    pos: BlockPos,
    state: BlockState,
) : BlockEntity(Content.FERMENTATION_BARREL_BLOCK_ENTITY.get(), pos, state) {

    val massContainer = MassContainer()

    fun use(pPlayer: Player, pHand: InteractionHand): InteractionResult {
        val stack = pPlayer.getItemInHand(pHand)

        if (stack.count == 0) {
            return InteractionResult.FAIL
        }

        if (stack.item == Items.SUGAR) {
            if (massContainer.getMass(glucose) > SUGAR_KG - 1.0) {
                return InteractionResult.FAIL
            }

            stack.count--
            pPlayer.setItemInHand(pHand, stack)

            massContainer.addMass(glucose, Quantity(1.0, KILOGRAMS))
        }

        return InteractionResult.FAIL
    }

    private fun serverTick() {
        massContainer.applyReaction(glucoseFermentationReaction, GLUCOSE_RATE)
        massContainer[O2] = Quantity(Double.MAX_VALUE)
        massContainer.applyReaction(aceticFermentationReaction, ACETIC_RATE)
    }

    companion object {
        private const val SUGAR_KG = 5.0
        private const val GLUCOSE_RATE = 0.001
        private const val ACETIC_RATE = 0.001

        fun serverTick(pLevel: Level, pPos: BlockPos, pState: BlockState, pBlockEntity: FermentationBarrelBlockEntity) {
            pBlockEntity.serverTick()
        }
    }
}
