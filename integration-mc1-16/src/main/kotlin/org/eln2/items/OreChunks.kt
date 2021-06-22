package org.eln2.items

import net.minecraft.client.util.ITooltipFlag
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraft.util.text.ITextComponent
import net.minecraft.util.text.TranslationTextComponent
import net.minecraft.world.World
import org.eln2.registry.genProperties
import org.eln2.registry.oreInformation
import org.eln2.utils.OreData

/**
 * It's a chunk of ore! This is what you get when you break the ore block.
 * Minecraft handing you a block that you can place makes ~0 sense.
 */
class OreChunks(oreData: OreData) : Item(genProperties(64)) {
    override fun appendHoverText(
        stack: ItemStack,
        worldIn: World?,
        tooltip: MutableList<ITextComponent>,
        flagIn: ITooltipFlag
    ) {
        super.appendHoverText(stack, worldIn, tooltip, flagIn)
        tooltip.add(TranslationTextComponent("tooltip.eln2.item.ore"))
    }

    init {
        this.registryName = ResourceLocation("eln2", "ore_chunks_${oreData.name}")
    }
}

val oreChunkMap = mutableMapOf<OreData, OreChunks>()

fun createOreChunks() {
    oreInformation.forEach {
        oreChunkMap[it] = OreChunks(it)
    }
}
