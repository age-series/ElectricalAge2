package org.eln2.parsers.falstad.components.sources

import org.eln2.parsers.falstad.CCData
import org.eln2.parsers.falstad.PoleConstructor
import org.eln2.sim.electrical.mna.component.Component
import org.eln2.sim.electrical.mna.component.VoltageSource

/**
 * Voltage Rail Constructor
 *
 * Basic Falstad Voltage Rail. I think this is a one pin with shared grounds?
 */
class VoltageRailConstructor : PoleConstructor() {
    override fun component(ccd: CCData) = VoltageSource()
    override fun configure(ccd: CCData, cmp: Component) {
        val v = (cmp as VoltageSource)
        v.potential = ccd.data[2].toDouble() + ccd.data[3].toDouble()
        v.ground(1) // nidx 1 should be neg
        ccd.pins = 1 // After the above--there are two pins, but the second is dropped
        ccd.falstad.floating = false
    }
}
