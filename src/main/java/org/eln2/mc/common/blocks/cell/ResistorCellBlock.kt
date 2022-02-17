package org.eln2.mc.common.blocks.cell

import net.minecraft.resources.ResourceLocation
import org.eln2.mc.common.cell.CellRegistry

class ResistorCellBlock : AbstractCellBlock() {
    override fun getCellProvider(): ResourceLocation {
        return CellRegistry.RESISTOR_CELL.id
    }
}
