package org.eln2.sim.electrical.mna

import org.eln2.debug.mnaPrintAll
import org.eln2.sim.electrical.mna.component.Resistor
import org.eln2.sim.electrical.mna.component.VoltageSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MNATests {

    @Test
    fun resistorVoltageSourceTest() {
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
        assertEquals(true, (r.current > 0.99) and (r.current < 1.01))
    }

    @Test
    fun resistorVoltageSourceModificationTest() {
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
        assertEquals(true, (r.current > 0.99) and (r.current < 1.01))

        r.resistance = 50.0

        c.step(0.05)

        mnaPrintAll(c.matrix!!, c.knowns!!, c.computeResult(), true)
        assertEquals(true, (r.current > 0.19) and (r.current < 0.21))
    }

    @Test
    fun twoResistorVoltageSourceModificationTest() {
        val c = Circuit()
        val r1 = Resistor()
        val r2 = Resistor()
        val vs = VoltageSource()

        c.add(vs, r1, r2)

        vs.connect(0, r1, 0)
        r1.connect(1, r2, 0)
        vs.connect(1, r2, 1)
        vs.connect(0, c.ground)

        vs.potential = 10.0
        r1.resistance = 10.0
        r2.resistance = 20.0

        c.step(0.05)
        mnaPrintAll(c.matrix!!, c.knowns!!, c.computeResult(), true)
        assertEquals(true, (r1.current > 0.333) and (r1.current < 0.334))
        assertEquals(true, (r2.current > 0.333) and (r2.current < 0.334))
        println(vs.nodes)
        println(r1.nodes)
        println(r2.nodes)
        assertEquals(true, (r1.nodes[1].potential > 3.333) and (r1.nodes[1].potential < 3.334))

        r2.resistance = 50.0

        c.step(0.05)

        mnaPrintAll(c.matrix!!, c.knowns!!, c.computeResult(), true)
        assertEquals(true, (r1.current > 0.1666) and (r1.current < 0.1667))
        assertEquals(true, (r2.current > 0.1666) and (r2.current < 0.1667))
        assertEquals(true, (r1.nodes[1].potential > 1.666) and (r1.nodes[1].potential < 1.667))
    }
}
