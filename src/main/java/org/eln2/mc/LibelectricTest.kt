package org.eln2.mc

import org.eln2.libelectric.sim.electrical.mna.Circuit
import org.eln2.libelectric.sim.electrical.mna.component.Resistor
import org.eln2.libelectric.sim.electrical.mna.component.VoltageSource

object LibelectricTest {
    fun test() {
        val thing = object: org.eln2.libelectric.sim.IProcess {
            override fun process(dt: Double) {
                Eln2.LOGGER.info("Got process with dt $dt")
            }
        }
        thing.process(0.5)

        // Example circuit roughly nabbed from libelectric test suite.
        val circuit = Circuit()
        val vs = VoltageSource()
        val rs = Resistor()

        vs.potential = 5.0
        rs.resistance = 100.0

        circuit.add(vs, rs)
        vs.connect(0, rs, 0)
        vs.connect(1, rs, 1)
        vs.ground(1)

        circuit.step(0.5)

        Eln2.LOGGER.info("Resistor current is ${rs.current}A")
    }
}
