package org.eln2.sim.electrical.mna

import org.eln2.debug.dprintln
import org.eln2.debug.mnaPrintln
import org.eln2.sim.electrical.mna.component.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.abs

internal class MNATests {

    /**
     *
     **/
    fun within_tolerable_error(simulated: Double, actual: Double, tolerance: Double) : Boolean {
        val percentError = abs((simulated - actual) / actual)
        dprintln("sim = $simulated, act = $actual, %err = $percentError")
        return percentError < tolerance
    }

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

        vs.connect(POSITIVE, c1, POSITIVE)
        c1.connect(NEGATIVE, r1, POSITIVE)
        r1.connect(NEGATIVE, vs, NEGATIVE)
        vs.ground(NEGATIVE)

        vs.potential = 5.0
        r1.resistance = 267.0 + 22.0
        c1.capacitance = 0.000932

        /*
            The following uses data from measuring real circuits rather than comparing against Falstad.
            See /testdata/discharging_data.ods
         */

        // Setting the tolerance of inaccuracy to 15% error.
        val tolerance = 0.15

        // Charging

        assert(c.step(0.05)) // 0.050 s
        assert(within_tolerable_error(r1.potential, 4.0909, tolerance))
        assert(within_tolerable_error(c1.potential, 5 - 4.0909, tolerance))
        assert(within_tolerable_error(c1.current, 0.0142, tolerance))

        assert(c.step(0.05)) // 0.100 s
        assert(within_tolerable_error(r1.potential, 3.4017, tolerance))
        assert(within_tolerable_error(c1.potential, 5 - 3.4017, tolerance))
        assert(within_tolerable_error(c1.current, 0.0118, tolerance))

        assert(c.step(0.05)) // 0.150 s
        assert(within_tolerable_error(r1.potential, 2.8250, tolerance))
        assert(within_tolerable_error(c1.potential, 5 - 2.8250, tolerance))
        assert(within_tolerable_error(c1.current, 0.0098, tolerance))

        assert(c.step(0.05)) // 0.200 s
        assert(within_tolerable_error(r1.potential, 2.3412, tolerance))
        assert(within_tolerable_error(c1.potential, 5 - 2.3412, tolerance))
        assert(within_tolerable_error(c1.current, 0.0081, tolerance))

        // Should finish charging within 2.00 seconds.
        for (it in 4..40) assert(c.step(0.05))

        // Discharging

        vs.potential = 0.0

        assert(c.step(0.05)) // 0.050 s
        assert(within_tolerable_error(c1.potential, 4.0909, tolerance))
        assert(within_tolerable_error(r1.potential, -4.0909, tolerance))
        assert(within_tolerable_error(c1.current, -0.0142, tolerance))

        assert(c.step(0.05)) // 0.100 s
        assert(within_tolerable_error(c1.potential, 3.4017, tolerance))
        assert(within_tolerable_error(r1.potential, -3.4017, tolerance))
        assert(within_tolerable_error(c1.current, -0.0118, tolerance))

        assert(c.step(0.05)) // 0.150 s
        assert(within_tolerable_error(c1.potential, 2.8250, tolerance))
        assert(within_tolerable_error(r1.potential,  -2.8250, tolerance))
        assert(within_tolerable_error(c1.current, -0.0098, tolerance))

        assert(c.step(0.05)) // 0.200 s
        assert(within_tolerable_error(c1.potential, 2.3412, tolerance))
        assert(within_tolerable_error(r1.potential, -2.3412, tolerance))
        assert(within_tolerable_error(c1.current, -0.0081, tolerance))
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
        i1.inductance = 0.05

        assert(c.step(0.05))
        mnaPrintln(c)
        println(i1.detail())
        println(r1.detail())
        assert(c.step(0.05))
        mnaPrintln(c)
        println(i1.detail())
        println(r1.detail())
        assert(c.step(0.05))
        mnaPrintln(c)
        println(i1.detail())
        println(r1.detail())
    }
}
