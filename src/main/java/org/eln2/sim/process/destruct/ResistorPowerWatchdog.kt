package org.eln2.sim.process.destruct

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.sim.mna.component.Resistor

class ResistorPowerWatchdog(var resistor: Resistor): ValueWatchdog() {
    var maximumPower: Double
        get(): Double {
            return max
        }
        set(mp) {
            max = mp * 1.2
            min = -1.0
        }

    override fun getValue(): Double {
        return resistor.getP()
    }
}