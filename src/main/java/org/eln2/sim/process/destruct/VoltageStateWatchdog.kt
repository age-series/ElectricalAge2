package org.eln2.sim.process.destruct

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.sim.mna.state.VoltageState
import kotlin.math.abs

open class VoltageStateWatchdog(val voltageState: VoltageState): ValueWatchdog() {
    override fun getValue(): Double {
        return voltageState.state
    }

    open fun setPositiveOnly(voltage: Double, tolerance: Double = TOLERANCE) {
        max = Math.abs(voltage) * 1.3
        min = -abs(tolerance)
    }

    open fun setNegativeOnly(voltage: Double, tolerance: Double = TOLERANCE) {
        min = abs(voltage)
        max = abs(tolerance)
    }

    open fun setPostiveAndNegative(a: Double, b: Double) {
        if (a > b) {
            max = a * 1.3
            min = b * 1.3
        } else {
            max = b * 1.3
            min = a * 1.3
        }
    }

    companion object {
        // According to some Arduino technical data, the most negative voltage the Arduino can handle is -0.5 volts.
        // for setPositiveOnly, the default is -0.5v
        // for setNegativeOnly, the default is 0.5v
        private const val TOLERANCE = 0.5
    }
}