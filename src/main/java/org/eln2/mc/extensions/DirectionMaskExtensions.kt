package org.eln2.mc.extensions

import org.eln2.mc.common.DirectionMask

object DirectionMaskExtensions {
    /**
     * Tries to match the source mask to the target mask, by rotating it clockwise a number of steps.
     * @return The number of steps needed to match the 2 masks, or -1 if no match was found.
     * */
    fun DirectionMask.matchClockwise(targetMask : DirectionMask) : Int{
        for(i in 0..3){
            val rotated = this.clockWise(i)

            if(targetMask == rotated){
                return i
            }
        }

        return -1
    }

    /**
     * Tries to match the source mask to the target mask, by rotating it counterclockwise a number of steps.
     * @return The number of steps needed to match the 2 masks, or -1 if no match was found.
     * */
    fun DirectionMask.matchCounterClockWise(targetMask : DirectionMask) : Int{
        for(i in 0..3){
            val rotated = this.counterClockWise(i)

            if(targetMask == rotated){
                return i
            }
        }

        return -1
    }
}
