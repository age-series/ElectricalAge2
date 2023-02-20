package org.eln2.mc.common.cells.foundation

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction

/**
 * Describes a cell's location.
 * A cell can be located by a block position, and a placement face.
 * */
data class CellPos(val blockPos: BlockPos, val face: Direction)
