package org.eln2.oldsim.thermal.process

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.oldsim.electrical.mna.state.ElectricalLoad
import org.eln2.sim.IProcess
import org.eln2.oldsim.thermal.ThermalLoad

class ElectricalLoadHeatThermalLoad(var r: ElectricalLoad, var load: ThermalLoad) : IProcess {
    override fun process(time: Double) {
        if (r.getNotSimulated()) return
        val I = r.getI()
        load.movePowerTo(I * I * r.r * 2)
    }
}