package org.eln2.mc.content.cell.wire

import net.minecraft.resources.ResourceLocation
import org.eln2.mc.common.cell.CellBlockBase
import org.eln2.mc.registry.CellRegistry

class WireCellBlock : CellBlockBase() {
    override fun getCellProvider(): ResourceLocation {
        return CellRegistry.WIRE_CELL.cellId()
    }
}
