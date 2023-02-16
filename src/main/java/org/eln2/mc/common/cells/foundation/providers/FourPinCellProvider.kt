package org.eln2.mc.common.cells.foundation.providers

import org.eln2.mc.common.RelativeRotationDirection
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.CellProvider

class FourPinCellProvider(val factory : ((pos : CellPos) -> CellBase)) : CellProvider() {
    init {
        connectableDirections.addAll(listOf(
            RelativeRotationDirection.Front,
            RelativeRotationDirection.Back,
            RelativeRotationDirection.Left,
            RelativeRotationDirection.Right
        ))
    }

    override fun createInstance(pos: CellPos): CellBase {
        return factory(pos)
    }

    override fun connectionPredicate(dir: RelativeRotationDirection): Boolean {
        return true
    }
}
