package org.eln2.oldsim.thermal.process

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.oldsim.electrical.mna.component.Resistor
import org.eln2.sim.IProcess
import org.eln2.oldsim.thermal.ThermalLoad

class ResistorHeatThermalLoad(var r: Resistor, var load: ThermalLoad) : IProcess {
    override fun process(time: Double) {
        load.movePowerTo(r.getP())
    }
}