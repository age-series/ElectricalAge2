package org.eln2.mc.extensions

import net.minecraft.core.Direction

object DirectionExtensions {
    fun Direction.isVertical() : Boolean{
        return this == Direction.UP || this == Direction.DOWN;
    }
}
