package org.eln2.mc.common.parts

import net.minecraft.core.Direction
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import org.eln2.mc.common.blocks.MultipartBlockEntity

data class PartPlacementContext(
    val pos : BlockPos,
    val face : Direction,
    val horizontalFacing : Direction,
    val level : Level,
    val multipart: MultipartBlockEntity)
