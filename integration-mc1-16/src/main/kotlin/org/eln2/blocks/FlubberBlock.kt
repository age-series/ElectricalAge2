package org.eln2.blocks

import net.minecraft.block.Blocks
import net.minecraft.block.SlimeBlock
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack
import net.minecraft.util.math.vector.Vector3d
import net.minecraft.util.text.ITextComponent
import net.minecraft.util.text.TranslationTextComponent
import net.minecraft.world.IBlockReader

/**
 * Flubber.
 *
 * This is flubber. Flubber is bouncy. Bounce bounce.
 */
class FlubberBlock : SlimeBlock(Properties.copy(Blocks.SLIME_BLOCK)) {

    /**
     * Called when an Entity lands on this Block. This method *must* update motionY because the entity will not do that
     * on its own
     */
    override fun updateEntityAfterFallOn(world: IBlockReader, entity: Entity) {
        if (entity.isSuppressingBounce) {
            super.updateEntityAfterFallOn(world, entity)
        } else {
            val motion: Vector3d = entity.deltaMovement
            entity.setDeltaMovement(motion.x, -motion.y * 1.6, motion.z)
        }
    }

    override fun appendHoverText(
        stack: ItemStack,
        worldIn: IBlockReader?,
        tooltip: MutableList<ITextComponent>,
        flagIn: ITooltipFlag
    ) {
        super.appendHoverText(stack, worldIn, tooltip, flagIn)
        tooltip.add(TranslationTextComponent("tooltip.eln2.block.flubber"))
    }
}
