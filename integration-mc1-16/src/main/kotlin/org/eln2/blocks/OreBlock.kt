package org.eln2.blocks
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.item.Item
import org.eln2.utils.OreData

/**
 * OreBlock - a block of ore.
 */
class OreBlock(val oreType: OreData) : Block(Properties.from(Blocks.IRON_ORE).hardnessAndResistance(oreType.hardness)) {
    // TODO: Figure out if this actually drops the block correctly.
    override fun asItem(): Item {
        return oreType.result.item
    }
    fun getRarity() : Int{
        return oreType.rarity
    }
}
