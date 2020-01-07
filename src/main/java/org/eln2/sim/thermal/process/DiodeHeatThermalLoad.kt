package org.eln2.sim.thermal.process

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.sim.mna.component.Resistor
import org.eln2.sim.process.IProcess
import org.eln2.sim.thermal.ThermalLoad

class DiodeHeatThermalLoad(var r: Resistor, var load: ThermalLoad) : IProcess {

    var lastR = 0.0

    init {
        lastR = r.r
    }

    override fun process(time: Double) {
        if (r.r == lastR) {
            load.movePowerTo(r.getP())
        } else {
            lastR = r.r
        }
    }
}