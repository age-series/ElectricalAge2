package org.eln2.mc.extensions

import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.minecraft.core.BlockPos
import org.eln2.mc.common.blocks.CellTileEntity

object LevelExtensions {
    /**
     * Queries the world for the tile present in the specified direction.
     * @param dir The direction to search in.
     * @param pos The position to look from.
     * @return The tile if found, or null if there is no tile at that position.
     */
    fun Level.getAdjacentTile(dir : Direction, pos : BlockPos) : CellTileEntity?{
        val remotePos = pos.relative(dir)
        val remoteEnt = this.getBlockEntity(remotePos)
        return remoteEnt as CellTileEntity?
    }
}
