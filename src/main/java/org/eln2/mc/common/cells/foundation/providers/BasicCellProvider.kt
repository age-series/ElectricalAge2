package org.eln2.mc.common.cells.foundation.providers

import net.minecraft.resources.ResourceLocation
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.CellProvider
import org.eln2.mc.common.space.DirectionMask
import org.eln2.mc.common.space.RelativeRotationDirection

/**
 * The Basic Provider is a cell provider that filters connections based on a direction mask.
 * */
class BasicCellProvider(private val directionMask: DirectionMask, private val factory: ICellFactory) : CellProvider() {
    override fun createInstance(pos: CellPos, id: ResourceLocation): CellBase {
        return factory.create(pos, id)
    }

    override fun canConnectFrom(direction: RelativeRotationDirection): Boolean {
        return directionMask.has(direction)
    }

    companion object {
        /**
         * Creates a basic provider that accepts connections from the front and back of the cell.
         * */
        fun polarFB(factory: ICellFactory): BasicCellProvider {
            return BasicCellProvider(DirectionMask.FRONT + DirectionMask.BACK, factory)
        }

        /**
         * Creates a basic provider that accepts connections from the front.
         * */
        fun monoF(factory: ICellFactory): BasicCellProvider {
            return BasicCellProvider(DirectionMask.FRONT, factory)
        }

        /**
         * Creates a basic provider that accepts connections from the back.
         * */
        fun monoB(factory: ICellFactory): BasicCellProvider {
            return BasicCellProvider(DirectionMask.BACK, factory)
        }

        /**
         * Creates a basic provider that accepts connections from the left.
         * */
        fun monoL(factory: ICellFactory): BasicCellProvider {
            return BasicCellProvider(DirectionMask.LEFT, factory)
        }

        /**
         * Creates a basic provider that accepts connections from the right.
         * */
        fun monoR(factory: ICellFactory): BasicCellProvider {
            return BasicCellProvider(DirectionMask.RIGHT, factory)
        }

        /**
         * Creates a basic provider that accepts connections from the left and right of the cell.
         * */
        fun polarLR(factory: ICellFactory): BasicCellProvider {
            return BasicCellProvider(DirectionMask.LEFT + DirectionMask.RIGHT, factory)
        }

        /**
         * Creates a basic provider that accepts connections from all 4 horizontal directions.
         * */
        fun fourPin(factory: ICellFactory): BasicCellProvider {
            return BasicCellProvider(DirectionMask.HORIZONTALS, factory)
        }
    }
}
