package org.eln2.mc.content.cell.inductor

import net.minecraft.resources.ResourceLocation
import org.eln2.mc.common.cell.CellBlockBase
import org.eln2.mc.registry.CellRegistry

class InductorCellBlock: CellBlockBase() {
    override fun getCellProvider(): ResourceLocation {
        return CellRegistry.INDUCTOR_CELL.id
    }
}
