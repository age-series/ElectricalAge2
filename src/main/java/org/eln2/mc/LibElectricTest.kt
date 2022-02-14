package org.eln2.mc

import org.eln2.libelectric.sim.electrical.mna.Circuit
import org.eln2.libelectric.sim.electrical.mna.component.Resistor
import org.eln2.libelectric.sim.electrical.mna.component.VoltageSource

object LibElectricTest {
    fun test() {
        val thing = object: org.eln2.libelectric.sim.IProcess {
            override fun process(dt: Double) {
                Eln2.LOGGER.info("Got process with dt $dt")
            }
        }
        thing.process(0.5)

        val circuit = Circuit()


        val vs = VoltageSource()
        vs.potential = 100.0
        val vsR = Resistor()
        val resistor = Resistor()
        val gndR = Resistor()

        circuit.add(vs, vsR, resistor, gndR)

        vs.ground(0)
        gndR.ground(0)

        // connect local VS resistor to VS

        vsR.connect(0, vs, 1)

        // connect resistor to ground and voltage
        resistor.connect(0, vsR, 1)
        resistor.connect(1, gndR, 1)

        // connect ground to resistor
        gndR.connect(1, resistor, 1)

        // connect voltage to resistor
        vsR.connect(1, resistor, 0)

        // p.s pin 1 is the "public" pin

        circuit.step(0.5)

        Eln2.LOGGER.info("Resistor current is ${resistor.current}")
    }
}
