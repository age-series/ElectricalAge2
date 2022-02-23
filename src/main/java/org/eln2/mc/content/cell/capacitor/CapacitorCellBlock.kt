package org.eln2.mc.content.cell.capacitor

import net.minecraft.resources.ResourceLocation
import org.eln2.mc.common.cell.CellBlockBase
import org.eln2.mc.registry.CellRegistry

class CapacitorCellBlock: CellBlockBase() {
    override fun getCellProvider(): ResourceLocation {
        return CellRegistry.CAPACITOR_CELL.id
    }
}
