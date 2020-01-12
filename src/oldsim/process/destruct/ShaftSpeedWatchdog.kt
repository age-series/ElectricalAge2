package org.eln2.oldsim.process.destruct

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

/*
class ShaftSpeedWatchdog(shaftElement: ShaftElement, max: Double) : ValueWatchdog() {
    val shaftElement = shaftElement

    init {
        this.max = max
    }

    override fun getValue(): Double {
        var max = 0.0
        shaftElement.shaftConnectivity.forEach {
            val shaft = shaftElement.getShaft(it)
            if(shaft != null) max = Math.max(max, shaft.rads)
        }
        return max
    }
}
*/