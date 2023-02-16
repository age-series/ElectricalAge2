package org.eln2.mc.common.cells.foundation.providers

import org.eln2.mc.Eln2
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.CellProvider
import org.eln2.mc.common.space.DirectionMask
import org.eln2.mc.common.space.RelativeRotationDirection

class PolarProvider(private val factory: ICellFactory) : CellProvider() {
    constructor(func : ((CellPos) -> CellBase)) : this(object : ICellFactory {
        override fun create(pos: CellPos): CellBase {
            return func(pos)
        }
    })

    companion object {
        val directions = DirectionMask.FRONT + DirectionMask.BACK
    }

    override fun createInstance(pos: CellPos): CellBase {
        return factory.create(pos)
    }

    override fun canConnectFrom(direction: RelativeRotationDirection): Boolean {
        Eln2.LOGGER.info("Query for $direction")

        return directions.has(direction)
    }
}
