package org.eln2.mc.common.cell.providers

import net.minecraft.core.BlockPos
import org.eln2.mc.Eln2
import org.eln2.mc.common.RelativeRotationDirection
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.CellProvider

class TwoPinCellProvider(val factory : ((pos : BlockPos) -> CellBase), override val symbol: Char = 'U') : CellProvider() {
    init {
        connectableDirections.addAll(listOf(
            RelativeRotationDirection.Front,
            RelativeRotationDirection.Back
        ))
    }

    override fun create(pos: BlockPos): CellBase {
        return factory(pos)
    }

    override fun connectionPredicate(dir: RelativeRotationDirection): Boolean {
        Eln2.LOGGER.info("DIR: $dir")
        return true
    }
}
