package org.eln2

import net.minecraft.block.Block
import org.eln2.blocks.FlubberBlock
import org.eln2.blocks.OreBlock
import org.eln2.utils.OreData

/**
 * Blocks added here are automatically registered.
 */
enum class ModBlocks(val block: Block) {
    FLUBBER(FlubberBlock()),
    ore_native_copper(OreBlock(OreData(1.0f, "ore_native_copper", ModItems.ore_chunks_copper.items.defaultInstance))),
    //NODE_BLOCK(NodeBlock())
}
