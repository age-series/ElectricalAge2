package org.eln2.mc.common.blocks.cell

import net.minecraft.resources.ResourceLocation
import org.eln2.mc.common.cell.CellRegistry

class InductorCellBlock : AbstractCellBlock() {
    override fun getCellProvider(): ResourceLocation {
        return CellRegistry.INDUCTOR_CELL.id
    }
}
