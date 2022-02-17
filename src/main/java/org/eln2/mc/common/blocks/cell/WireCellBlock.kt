package org.eln2.mc.common.blocks.cell

import net.minecraft.resources.ResourceLocation
import org.eln2.mc.common.cell.CellRegistry

class WireCellBlock : AbstractCellBlock() {
    override fun getCellProvider(): ResourceLocation {
        return CellRegistry.WIRE_CELL.id
    }
}
