package org.eln2.mc.common.cells.foundation.providers

import net.minecraft.resources.ResourceLocation
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos

fun interface ICellFactory {
    fun create(pos: CellPos, id: ResourceLocation): CellBase
}
