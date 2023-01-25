package org.eln2.mc.common.blocks

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class MultipartBlockEntity (var pos : BlockPos, var state: BlockState): BlockEntity(BlockRegistry.MULTIPART_BLOCK_ENTITY.get(), pos, state) {
}
