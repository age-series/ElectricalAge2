package org.eln2.items

import net.minecraft.client.util.ITooltipFlag
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraft.util.text.ITextComponent
import net.minecraft.util.text.TranslationTextComponent
import net.minecraft.world.World

/**
 * Multimeter
 *
 * This is used to measure the voltage of a circuit between the circuit and ground.
 * It also displays other useful information for some components.
 */
class MultimeterItem : Item(Properties().defaultDurability(255)) {
    override fun appendHoverText(
        stack: ItemStack,
        worldIn: World?,
        tooltip: MutableList<ITextComponent>,
        flagIn: ITooltipFlag
    ) {
        super.appendHoverText(stack, worldIn, tooltip, flagIn)
        tooltip.add(TranslationTextComponent("tooltip.eln2.item.multimeter"))
    }

    init {
        this.registryName = ResourceLocation("eln2", "multimeter")
    }
}
