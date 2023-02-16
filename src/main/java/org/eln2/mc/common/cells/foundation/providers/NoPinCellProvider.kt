package org.eln2.mc.common.cells.foundation.providers

import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.CellProvider
import org.eln2.mc.common.space.RelativeRotationDirection

class NoPinCellProvider(val factory: ((pos: CellPos) -> CellBase)) : CellProvider() {
    override fun createInstance(pos: CellPos): CellBase {
        return factory(pos)
    }

    override fun connectionPredicate(dir: RelativeRotationDirection): Boolean {
        return true
    }
}
