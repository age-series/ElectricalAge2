package org.eln2.mc.content.cell.ground

import net.minecraft.resources.ResourceLocation
import org.eln2.mc.common.cell.CellBlockBase
import org.eln2.mc.registry.CellRegistry

class GroundCellBlock : CellBlockBase() {
    override fun getCellProvider(): ResourceLocation {
        return CellRegistry.GROUND_CELL.id
    }
}
