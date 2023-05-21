package org.eln2.mc.common.cells.foundation.providers

import net.minecraft.resources.ResourceLocation
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.CellProvider
import org.eln2.mc.common.space.DirectionMask

class BasicCellProvider(private val factory: ICellFactory) : CellProvider() {
    override fun createInstance(pos: CellPos, id: ResourceLocation): CellBase {
        return factory.create(pos, id)
    }
}
