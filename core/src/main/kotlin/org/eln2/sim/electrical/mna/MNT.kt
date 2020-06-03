package org.eln2.sim.electrical.mna

import org.eln2.debug.mnaPrint
import org.eln2.debug.mnaPrintAll
import org.eln2.sim.electrical.mna.Circuit
import org.eln2.sim.electrical.mna.component.Resistor
import org.eln2.sim.electrical.mna.component.VoltageSource

class MNT {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val c = Circuit()
            val r = Resistor()
            val vs = VoltageSource()

            c.add(vs, r)

            vs.connect(0, r, 0)
            vs.connect(1, r, 1)
            vs.connect(0, c.ground)

            vs.potential = 10.0
            r.resistance = 10.0

            c.step(0.05)

            mnaPrintAll(c.matrix!!, c.knowns!!, c.computeResult(), true)
        }
    }
}

