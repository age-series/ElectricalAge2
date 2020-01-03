package org.eln2.sim.mna.state

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.sim.mna.component.Bipole
import org.eln2.sim.mna.component.ElectricalConnection
import org.eln2.sim.mna.component.Line
import org.eln2.sim.mna.misc.MnaConst

open class ElectricalLoad: VoltageState() {

    open var r: Double = 1.0
        set(r) {
            if (r != field) {
                field = r
                for (c in components) {
                    if (c is ElectricalConnection) {
                        c.notifyRsChange()
                    }
                }
            }
        }

    fun highImpedance() {
        r = MnaConst.highImpedance
    }

    fun getI(): Double {
        var i = 0.0
        for (c in components) {
            if (c is Bipole && c !is Line) i += Math.abs(c.getI())
        }
        return i * 0.5
    }

}