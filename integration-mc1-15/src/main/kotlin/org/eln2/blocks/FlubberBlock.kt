package org.eln2.blocks

import net.minecraft.block.Blocks
import net.minecraft.block.SlimeBlock
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack
import net.minecraft.util.math.Vec3d
import net.minecraft.util.text.ITextComponent
import net.minecraft.util.text.TranslationTextComponent
import net.minecraft.world.IBlockReader

/**
 * Flubber.
 *
 * This is flubber. Flubber is bouncy. Bounce bounce.
 */
class FlubberBlock : SlimeBlock(Properties.from(Blocks.SLIME_BLOCK)) {

    /**
     * Called when an Entity lands on this Block. This method *must* update motionY because the entity will not do that
     * on its own
     */
    override fun onLanded(world: IBlockReader, entity: Entity) {
        if (entity.isSuppressingBounce) {
            super.onLanded(world, entity)
        } else {
            val motion: Vec3d = entity.motion
            entity.setMotion(motion.x, -motion.y * 1.6, motion.z)
        }
    }

    override fun addInformation(
        stack: ItemStack,
        worldIn: IBlockReader?,
        tooltip: MutableList<ITextComponent>,
        flagIn: ITooltipFlag
    ) {
        super.addInformation(stack, worldIn, tooltip, flagIn)
        tooltip.add(TranslationTextComponent("tooltip.eln2.block.flubber"))
    }
}
