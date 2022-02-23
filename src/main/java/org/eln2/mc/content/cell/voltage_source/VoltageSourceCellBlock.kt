package org.eln2.mc.content.cell.voltage_source

import net.minecraft.resources.ResourceLocation
import org.eln2.mc.common.cell.CellBlockBase
import org.eln2.mc.registry.CellRegistry

class VoltageSourceCellBlock : CellBlockBase() {
    override fun getCellProvider(): ResourceLocation {
        return CellRegistry.VOLTAGE_SOURCE_CELL.cellId()
    }
}
