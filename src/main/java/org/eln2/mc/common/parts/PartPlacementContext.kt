package org.eln2.mc.common.parts

import net.minecraft.core.Direction
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

data class PartPlacementContext(
    val pos : BlockPos,
    val face : Direction,
    val level : Level)
