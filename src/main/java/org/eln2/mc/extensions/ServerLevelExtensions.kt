package org.eln2.mc.extensions

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import org.eln2.mc.common.blocks.CellBlockEntity
import org.eln2.mc.common.cell.CellBase

object ServerLevelExtensions {
    fun ServerLevel.getCellAt(pos : BlockPos) : CellBase {
        return (this.getBlockEntity(pos) as CellBlockEntity).cell!!
    }

    fun ServerLevel.getCellEntityAt(pos : BlockPos) : CellBlockEntity {
        return this.getBlockEntity(pos) as CellBlockEntity
    }
}
