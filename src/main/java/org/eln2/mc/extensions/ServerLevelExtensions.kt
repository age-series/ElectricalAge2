package org.eln2.mc.extensions

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.CellTileEntity

object ServerLevelExtensions {
    fun ServerLevel.getCellAt(pos : BlockPos) : CellBase {
        return (this.getBlockEntity(pos) as CellTileEntity).cell!!
    }

    fun ServerLevel.getCellEntityAt(pos : BlockPos) : CellTileEntity {
        return this.getBlockEntity(pos) as CellTileEntity
    }
}
