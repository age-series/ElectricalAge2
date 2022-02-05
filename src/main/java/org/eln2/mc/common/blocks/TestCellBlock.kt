package org.eln2.mc.common.blocks

import net.minecraft.resources.ResourceLocation
import org.eln2.mc.common.cell.CellRegistry

class TestCellBlock : CellBlockBase() {
    override fun getCellProvider(): ResourceLocation {
        return CellRegistry.TEST_CELL.id
    }
}
