package org.eln2.mc.common.cell.providers

import net.minecraft.core.BlockPos
import org.eln2.mc.common.RelativeRotationDirection
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.CellPos
import org.eln2.mc.common.cell.CellProvider

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
