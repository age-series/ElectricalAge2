package org.eln2.mc.common.blocks.cell

import net.minecraft.resources.ResourceLocation
import org.eln2.mc.common.blocks.CellBlockBase
import org.eln2.mc.common.cell.CellRegistry

class GroundCellBlock : CellBlockBase() {
    override fun getCellProvider(): ResourceLocation {
        return CellRegistry.GROUND_CELL.id
    }
}
