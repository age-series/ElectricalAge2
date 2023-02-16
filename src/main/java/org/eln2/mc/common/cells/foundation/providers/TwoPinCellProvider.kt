package org.eln2.mc.common.cells.foundation.providers

import org.eln2.mc.Eln2
import org.eln2.mc.common.RelativeRotationDirection
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.CellProvider

class TwoPinCellProvider(val factory : ((pos : CellPos) -> CellBase)) : CellProvider() {
    init {
        connectableDirections.addAll(listOf(
            RelativeRotationDirection.Front,
            RelativeRotationDirection.Back
        ))
    }

    override fun createInstance(pos: CellPos): CellBase {
        return factory(pos)
    }

    override fun connectionPredicate(dir: RelativeRotationDirection): Boolean {
        Eln2.LOGGER.info("DIR: $dir")
        return true
    }
}
