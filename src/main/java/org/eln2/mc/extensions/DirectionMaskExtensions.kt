package org.eln2.mc.extensions

import net.minecraft.core.Direction
import org.eln2.mc.common.DirectionMask

object DirectionMaskExtensions {
    operator fun DirectionMask.plus(other : DirectionMask) : DirectionMask{
        return DirectionMask(this.mask or other.mask)
    }

    operator fun DirectionMask.plus(direction: Direction) : DirectionMask{
        return DirectionMask(this.mask or DirectionMask.getDirectionBit(direction))
    }

    operator fun DirectionMask.minus(direction: Direction) : DirectionMask{
        return DirectionMask(this.mask and DirectionMask.getDirectionBit(direction).inv())
    }
}
