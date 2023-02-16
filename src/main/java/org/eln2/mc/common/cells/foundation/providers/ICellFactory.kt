package org.eln2.mc.common.cells.foundation.providers

import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos

@FunctionalInterface
interface ICellFactory {
    fun create(pos : CellPos) : CellBase
}
