package org.eln2.sim.mna.process

import org.eln2.sim.mna.component.ResistorSwitch
import org.eln2.sim.process.IProcess

open class DiodeProcess(open var resistor: ResistorSwitch) : IProcess {
    override fun process(time: Double) {
        resistor.state = resistor.u > 0
    }
}