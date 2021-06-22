package org.eln2.blocks
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.item.Item
import net.minecraft.util.ResourceLocation
import org.eln2.items.oreChunkMap
import org.eln2.registry.oreInformation
import org.eln2.utils.OreData
import java.lang.NullPointerException

/**
 * OreBlock - a block of ore.
 */
class OreBlock(val oreType: OreData) : Block(Properties.copy(Blocks.IRON_ORE).harvestLevel(oreType.hardness)) {
    override fun asItem(): Item {
        return oreChunkMap[oreType]?: throw NullPointerException("Ore type not found!")
    }

    fun getRarity() : Int{
        return oreType.rarity
    }

    init {
        this.registryName = ResourceLocation("eln2", "ore_${oreType.name}")
    }
}

val oreBlockMap = mutableMapOf<OreData, OreBlock>()

fun createOreBlocks() {
    oreInformation.forEach {
        oreBlockMap[it] = OreBlock(it)
    }
}
