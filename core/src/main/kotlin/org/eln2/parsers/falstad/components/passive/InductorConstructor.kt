package org.eln2.parsers.falstad.components.passive

import org.eln2.parsers.falstad.CCData
import org.eln2.parsers.falstad.PoleConstructor
import org.eln2.sim.electrical.mna.component.Component
import org.eln2.sim.electrical.mna.component.Inductor

/**
 * Inductor Constructor
 *
 * Basic Falstad Inductor
 */
class InductorConstructor : PoleConstructor() {
    override fun component(ccd: CCData) = Inductor()
    override fun configure(ccd: CCData, cmp: Component) {
        val l = (cmp as Inductor)
        l.timeStep = ccd.falstad.nominalTimestep
        l.inductance = ccd.data[0].toDouble()
        l.internalCurrent = ccd.data[1].toDouble()
    }
}
