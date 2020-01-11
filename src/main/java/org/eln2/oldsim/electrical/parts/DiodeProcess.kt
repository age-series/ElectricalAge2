package org.eln2.oldsim.electrical.parts

import org.eln2.sim.IProcess

open class DiodeProcess(open var resistor: ResistorSwitch) : IProcess {
    override fun process(time: Double) {
        resistor.state = resistor.u > 0
    }
}