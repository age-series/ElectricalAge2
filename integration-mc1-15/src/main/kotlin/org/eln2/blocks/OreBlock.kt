package org.eln2.blocks

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.world.storage.loot.LootContext
import org.eln2.utils.OreData

/**
 * OreBlock - a block of ore.
 */
class OreBlock(val oreType: OreData) : Block(Properties.from(Blocks.IRON_ORE).hardnessAndResistance(oreType.hardness)) {
    /**
     * getDrops override so that we drop the ore dust.
     *
     * Yes, it's deprecated, no, I don't care. I dare you to look at the supported method, if you can find it.
     * TODO: Stop using deprecated function
     */
    override fun getDrops(state: BlockState, builder: LootContext.Builder): MutableList<ItemStack> {
        return mutableListOf(oreType.result.item.defaultInstance)
    }
}
