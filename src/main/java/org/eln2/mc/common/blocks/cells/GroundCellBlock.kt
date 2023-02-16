package org.eln2.mc.common.blocks.cells

import net.minecraft.resources.ResourceLocation
import org.eln2.mc.common.blocks.foundation.CellBlock
import org.eln2.mc.common.cells.CellRegistry

class GroundCellBlock : CellBlock() {
    override fun getCellProvider(): ResourceLocation {
        return CellRegistry.GROUND_CELL.id
    }
}
