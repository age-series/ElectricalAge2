package org.eln2.registry

import net.minecraft.block.Block;
import org.eln2.blocks.FlubberBlock
import org.eln2.node.NodeBlock

enum class MiscBlocks(val block:Block) {
    FLUBBER_BLOCK(FlubberBlock()),
    TEST_NODE(NodeBlock())
}
