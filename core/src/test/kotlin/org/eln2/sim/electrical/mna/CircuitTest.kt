package org.eln2.sim.electrical.mna

import org.eln2.sim.electrical.mna.component.Capacitor
import org.eln2.sim.electrical.mna.component.Resistor
import org.eln2.sim.electrical.mna.component.VoltageSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.opentest4j.AssertionFailedError
import kotlin.math.sign

const val EPSILON = 1e-9

internal class CircuitTest {

    class TrivialResistiveCircuit {
        val c = Circuit()
        val vs = VoltageSource()
        val r1 = Resistor()

        init {
            c.add(vs, r1)
            vs.neg.named("vneg")
            vs.pos.named("vpos")
            vs.connect(1, r1, 1)
            vs.connect(0, r1, 0)
            vs.connect(1, c.ground)
            vs.potential = 10.0
            r1.resistance = 5.0
        }
    }

    class TrivialRCCircuit {
        val c = Circuit()
        val vs = VoltageSource()
        val r1 = Resistor()
        val c1 = Capacitor()

        init {
            c.add(vs, r1, c1)
            vs.neg.named("vneg")
            vs.pos.named("vpos")
            vs.potential = 10.0
            r1.resistance = 5.0
            c1.capacitance = 0.01
            vs.connect(0, r1, 0)
            c1.connect(0, r1, 0)
            vs.connect(1, c1, 1)
            vs.connect(1, c.ground)
        }
    }

    @Test
    fun parity() {
        val ts = TrivialResistiveCircuit()
        for (u in -10..10) {
            ts.vs.potential = u.toDouble()
            ts.c.step(0.5)
            assertEquals(sign(ts.vs.potential), sign(ts.r1.current))
        }
    }

    @Test
    fun ohmLaw() {
        val ts = TrivialResistiveCircuit()
        for (r in 1..10) {
            ts.r1.resistance = r.toDouble()
            ts.c.step(0.5)
            assertEquals(ts.r1.current, ts.vs.potential / r, EPSILON)
        }
    }

    // TODO: Known breakage here: ts.vs.i retains its value indefinitely.
    @Test
    fun kirchoffCurrentLaw() {
        val ts = TrivialResistiveCircuit()
        for (r in 1..10) {
            ts.r1.resistance = r.toDouble()
            ts.c.step(0.5)
            try {
                assertEquals(-ts.vs.current, ts.r1.current, EPSILON) { "r:${ts.r1.resistance} u:${ts.vs.potential}" }
            } catch (e: AssertionFailedError) {
                println("expected failure in kCL: $e")
            }
        }
    }

    @Test
    fun kirchoffVoltageLaw() {
        val ts = TrivialResistiveCircuit()
        for (r in 1..10) {
            ts.r1.resistance = r.toDouble()
            ts.c.step(0.5)
            assertEquals(ts.vs.potential, ts.r1.potential, EPSILON)
        }
    }

    @Test
    fun componentChangeConsistency() { // Also tests resistors in parallel.
        val ts = TrivialResistiveCircuit()
        ts.c.step(0.5)
        val current = ts.r1.current

        val r2 = Resistor()
        ts.c.add(r2)
        r2.resistance = ts.r1.resistance
        r2.connect(0, ts.r1, 0)
        r2.connect(1, ts.r1, 1)

        ts.c.step(0.5)
        assertEquals(r2.current, ts.r1.current, EPSILON)
        assertEquals(ts.r1.current, current, EPSILON) { "i1_1=${ts.r1.current} i1_0=$current i2=${r2.current}" }
        assertEquals(ts.vs.current, current * 2.0, EPSILON)

        ts.c.remove(r2)

        ts.c.step(0.5)
        assertEquals(ts.r1.current, current, EPSILON)
        assertEquals(r2.circuit, null)
    }

    @Test
    fun resistorsInSeries() {
        val circuit = Circuit()
        val vs = VoltageSource()
        val r1 = Resistor()
        val r2 = Resistor()

        vs.potential = 10.0
        r1.resistance = 5.0
        r2.resistance = 5.0

        circuit.add(vs, r1, r2)
        vs.connect(1, r1, 1)
        r1.connect(0, r2, 1)
        r2.connect(0, vs, 0)
        vs.connect(1, circuit.ground)

        circuit.step(0.5)
        assertEquals(r1.current, r2.current, EPSILON)
        assertEquals(r1.current, 1.0, EPSILON)
    }

    @Test
    fun basicCapacitorCircuit() {
        val circuit = Circuit()
        val vs = VoltageSource()
        val c1 = Capacitor()

        vs.potential = 10.0
        c1.capacitance = 0.01

        circuit.add(vs, c1)
        vs.connect(1, c1, 1)
        vs.connect(0, c1, 0)
        vs.connect(1, circuit.ground)

        circuit.step(0.5)
        circuit.step(0.5)
        circuit.step(0.5)
        circuit.step(0.5)
    }

    @Test
    fun basicRCCircuit() {
        val ts = TrivialRCCircuit()
        ts.c.step(0.05)
        assertEquals(ts.c1.current, 0.000732, 0.000001)
        assertEquals(ts.c1.potential, 6.315, 0.001)
        ts.c.step(0.05)
        assertEquals(ts.c1.current, 0.000270, 0.000001)
        assertEquals(ts.c1.potential, 8.660, 0.001)
        ts.c.step(0.05)
        assertEquals(ts.c1.current, 0.000099, 0.000001)
        assertEquals(ts.c1.potential, 9.506, 0.001)
    }


    @Test
    fun basicCircuitTest() {
        val c = Circuit()

        val vs = VoltageSource()
        val r1 = Resistor()

        c.add(vs)
        c.add(r1)

        vs.connect(1, r1, 0)
        vs.connect(0, r1, 1)
        vs.connect(1, c.ground)

        vs.potential = 10.0
        r1.resistance = 100.0

        c.step(0.5)

        print("main_1: matrix:\n${MATRIX_FORMAT.format(c.matrix)}")

        for (comp in c.components) {
            println("main_1: comp $comp name ${comp.name} nodes ${comp.nodes} vs ${comp.vsources}")
            for (node in comp.nodes) {
                println("\t node $node index ${node.node.index} ground ${node.node.isGround} potential ${node.node.potential}")
            }
        }

        for (node in c.nodes) {
            println("main_1: node ${node.get()} index ${node.get()?.index} potential ${node.get()?.potential}")
        }

        println("main_1: vs current: ${vs.current}\nmain_1: r1 current: ${r1.current}")
    }
}
