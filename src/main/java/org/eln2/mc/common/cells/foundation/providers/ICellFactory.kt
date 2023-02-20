package org.eln2.mc.common.cells.foundation.providers

import net.minecraft.resources.ResourceLocation
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos

/**
 * The cell factory is used by Cell Providers, to instantiate Cells.
 * Usually, the constructor of the cell can be passed as factory.
 * */
fun interface ICellFactory {
    fun create(pos: CellPos, id: ResourceLocation): CellBase
}
