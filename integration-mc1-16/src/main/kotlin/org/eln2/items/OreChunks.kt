package org.eln2.items

import net.minecraft.client.util.ITooltipFlag
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.text.ITextComponent
import net.minecraft.util.text.TranslationTextComponent
import net.minecraft.world.World
import org.eln2.registry.genProperties

/**
 * It's a chunk of ore! This is what you get when you break the ore block.
 * Minecraft handing you a block that you can place makes ~0 sense.
 */
class OreChunks : Item(genProperties(64)) {
    override fun appendHoverText(
        stack: ItemStack,
        worldIn: World?,
        tooltip: MutableList<ITextComponent>,
        flagIn: ITooltipFlag
    ) {
        super.appendHoverText(stack, worldIn, tooltip, flagIn)
        tooltip.add(TranslationTextComponent("tooltip.eln2.item.ore"))
    }


}
