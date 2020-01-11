package org.eln2.oldsim.process.destruct

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.sim.IProcess

abstract class ValueWatchdog: IProcess {
    var min: Double = 0.0
    var max: Double = 0.0

    override fun process(time: Double) {
        val value = getValue()
        if ((value > max) or (value < min)) {
            triggered()
        }
    }

    abstract fun getValue(): Double
    open fun triggered() {}
}