package org.eln2.mc.common.parts.foundation

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import org.eln2.mc.common.blocks.foundation.MultipartBlockEntity

/**
 * Encapsulates all the data associated with a part's placement, position, ...
 * */
data class PartPlacementContext(
    val pos: BlockPos,
    val face: Direction,
    val horizontalFacing: Direction,
    val level: Level,
    val multipart: MultipartBlockEntity
)
