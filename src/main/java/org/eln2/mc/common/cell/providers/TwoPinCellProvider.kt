package org.eln2.mc.common.cell.providers

import net.minecraft.core.BlockPos
import org.eln2.mc.common.RelativeRotationDirection
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.CellProvider
import org.eln2.mc.common.cell.types.ResistorCell

class TwoPinCellProvider(val factory : ((pos : BlockPos) -> CellBase), override val symbol: Char) : CellProvider() {
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
        return true
    }
}
