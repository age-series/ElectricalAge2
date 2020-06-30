package org.eln2.sim.electrical.mna

import org.eln2.debug.mnaPrintln
import org.eln2.sim.electrical.mna.component.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class MNATests {

    @Test
    fun resistorVoltageSourceTest() {
        val c = Circuit()
        val r = Resistor()
        val vs = VoltageSource()

        c.add(vs, r)

        vs.connect(0, r, 0)
        vs.connect(1, r, 1)
        vs.ground(0)

        vs.potential = 10.0
        r.resistance = 10.0

        assert(c.step(0.05))
        mnaPrintln(c)
        assertEquals(true, (r.current > 0.99) and (r.current < 1.01))
    }

    @Test
    fun resistorCurrentSourceTest() {
        val c = Circuit()
        val r = Resistor()
        val cs = CurrentSource()

        c.add(cs, r)

        cs.connect(POSITIVE, r, POSITIVE)
        cs.connect(NEGATIVE, r, NEGATIVE)
        cs.ground(NEGATIVE)

        cs.current = -1.0
        r.resistance = 10.0

        assert(c.step(0.05))
        mnaPrintln(c)
        assertEquals(true, (r.current > 0.99) and (r.current < 1.01))
        assertEquals(true, (r.potential > 9.99) and (r.current < 10.01))
        assertEquals(true, (cs.potential > 9.99) and (cs.current < 10.01))
    }

    @Test
    fun resistorVoltageSourceModificationTest() {
        val c = Circuit()
        val r = Resistor()
        val vs = VoltageSource()

        c.add(vs, r)

        vs.connect(0, r, 0)
        vs.connect(1, r, 1)
        vs.ground(0)

        vs.potential = 10.0
        r.resistance = 10.0

        assert(c.step(0.05))
        mnaPrintln(c)
        assertEquals(true, (r.current > 0.99) and (r.current < 1.01))

        r.resistance = 50.0

        assert(c.step(0.05))

        mnaPrintln(c)
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
        vs.ground(0)

        vs.potential = 10.0
        r1.resistance = 10.0
        r2.resistance = 20.0

        assert(c.step(0.05))
        mnaPrintln(c)
        assertEquals(true, (r1.current > 0.333) and (r1.current < 0.334))
        assertEquals(true, (r2.current > 0.333) and (r2.current < 0.334))
        println(vs.pins)
        println(r1.pins)
        println(r2.pins)
        assertEquals(true, ((r1.node(1)?.potential ?: 0.0) > 3.333) and ((r1.node(1)?.potential ?: 0.0) < 3.334))

        r2.resistance = 50.0

        assert(c.step(0.05))

        mnaPrintln(c)
        assertEquals(true, (r1.current > 0.1666) and (r1.current < 0.1667))
        assertEquals(true, (r2.current > 0.1666) and (r2.current < 0.1667))
        assertEquals(true, ((r1.node(1)?.potential ?: 0.0)> 1.666) and ((r1.node(1)?.potential ?: 0.0) < 1.667))
    }

    @Test
    fun resistorCapacitorTest() {
        val c = Circuit()
        val r1 = Resistor()
        val c1 = Capacitor()
        val vs = VoltageSource()

        c.add(r1, c1, vs)

        vs.connect(POSITIVE, r1, POSITIVE)
        r1.connect(NEGATIVE, c1, POSITIVE)
        c1.connect(NEGATIVE, vs, NEGATIVE)
        vs.ground(NEGATIVE)

        vs.potential = 10.0
        r1.resistance = 100.0
        c1.capacitance = 0.05

        /* Voltage and current equations for this circuit:
            I(t) = Vs/R * e^(-t/RC) = e^(-t/5)/10
            V_C(t) = Vs * (1-e^(-t/RC)) = 10 - 10e^(-t/5)
            V_R(t) = Vs * e^(-t/RC) = 10e^(-t/5)
         */

        assert(c.step(0.05)) // 0.05 seconds after sim start.
        assert((r1.current > 0.098) and (r1.current < 0.100)) // Should be e^(-1/100)/10 (~0.099005) A
        assert((c1.current > 0.098) and (c1.current < 0.100)) // Ditto
        assert((r1.potential > 9.8) and (r1.potential < 10.0)) // Should be 10e^(-1/100) (~) V
        assert((c1.potential > 0.098) and (c1.potential < 0.100)) // Should be 10 - 10e^(-1/100) (~0.09950) V

        assert(c.step(0.05)) // 0.10 seconds after sim start.
        assert((r1.current > 0.098) and (r1.current < 0.099)) // Should be e^(-1/50)/10 (~0.098020) A
        assert((c1.current > 0.098) and (c1.current < 0.099)) // Ditto
        assert((r1.potential > 9.8) and (r1.potential < 9.9)) // Should be 10e^(-1/50) (~9.80199) V
        assert((c1.potential > 0.197) and (c1.potential < 0.199)) // Should be 10 - 10e^(-1/50) (~0.198013) V

        for (it in 2..100) assert(c.step(0.05)) // 1.00 seconds after sim start.
        assert((r1.current > 0.035) and (r1.current < 0.037)) // Should be 0.1/e (~0.0367879) A
        assert((c1.current > 0.035) and (c1.current < 0.037)) // Ditto
        assert((r1.potential > 3.5) and (r1.potential < 3.7)) // Should be 10/e (~3.67879) V
        assert((c1.potential > 6.2) and (c1.potential < 6.4)) // Should be 10 - 10/e (~6.32121) V
    }

    @Test
    fun resistorInductorTest() {
        val c = Circuit()
        val r1 = Resistor()
        val i1 = Inductor()
        val vs = VoltageSource()

        c.add(r1, i1, vs)

        vs.connect(POSITIVE, r1, POSITIVE)
        r1.connect(NEGATIVE, i1, POSITIVE)
        i1.connect(NEGATIVE, vs, NEGATIVE)
        vs.ground(NEGATIVE)

        vs.potential = 10.0
        r1.resistance = 20.0
        i1.inductance = 1.0

        assert(c.step(0.05))
        assert(c.step(0.05))
        assert(c.step(0.05))
    }
}
