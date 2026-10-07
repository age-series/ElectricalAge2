import org.ageseries.libage.mathematics.rounded
import org.ageseries.libage.sim.electrical.Capacitor
import org.ageseries.libage.sim.electrical.CurrentSource
import org.ageseries.libage.sim.electrical.ElectricalCircuitForestBuilder
import org.ageseries.libage.sim.electrical.ElectricalSimulation
import org.ageseries.libage.sim.electrical.Inductor
import org.ageseries.libage.sim.electrical.PotentialSource
import org.ageseries.libage.sim.electrical.PowerConsumer
import org.ageseries.libage.sim.electrical.PowerSource
import org.ageseries.libage.sim.electrical.Resistor
import org.ageseries.libage.sim.electrical.ResistorSystem
import org.ageseries.libage.utils.measureDuration
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class ElectricalTests {
    companion object {
        private fun ElectricalCircuitForestBuilder.testBuild(optimization: Boolean = true) : ElectricalSimulation {
            val subSolvers = this.build(
                1.0 / 100.0,
                optimization,
                ElectricalSimulation.ConstructionOptions(
                    1e15 // Fails with 1e14 for example, the tolerances I set for the tests are too tight. maybe fix
                )).solvers
            Assertions.assertEquals(subSolvers.size, 1)
            return subSolvers.first()
        }
    }

    //#region Fundamental Tests

    /**
     * Tests that the simulation throws an error if step() is called after destroy().
     * */
    @Test
    fun testStepAfterDestroy() {
        val builder = ElectricalCircuitForestBuilder()
        val r = Resistor()
        builder.add(r)
        builder.ground(r.negative)

        val circuit = builder.testBuild()
        circuit.step()

        circuit.destroy()

        assertThrows<IllegalStateException> {
            circuit.step()
        }
    }

    /**
     * Makes sure that the results are the same, regardless of the chosen reference node.
     * */
    @Test
    fun testGroundInvariance() {
        fun create(flag: Boolean): Triple<ElectricalSimulation, PotentialSource, Resistor> {
            val builder = ElectricalCircuitForestBuilder()
            val v1 = PotentialSource().also { it.potential = 10.0 }
            val r1 = Resistor()

            builder.add(v1, r1)
            builder.join(v1.positive, r1.positive)
            builder.join(v1.negative, r1.negative)

            if(flag) {
                builder.ground(v1.positive)
            }
            else {
                builder.ground(v1.negative)
            }

            return Triple(builder.testBuild(), v1, r1)
        }

        val (ca, va, ra) = create(true)
        val (cb, vb, rb) = create(false)

        repeat(10) {
            ca.step()
            cb.step()

            Assertions.assertTrue(va.power > 0.0 && vb.power > 0.0)
            Assertions.assertTrue(va.current == vb.current)
            Assertions.assertTrue(va.potential == vb.potential)
            Assertions.assertTrue(ra.current == rb.current)
        }
    }

    /**
     * Creates a singular system and makes sure the step errors.
     * */
    @Test
    fun testSingularSystem() {
        val builder = ElectricalCircuitForestBuilder()

        val v1 = PotentialSource()
        val v2 = PotentialSource()

        builder.add(v1, v2)

        builder.join(v1.positive, v2.positive)
        builder.join(v1.negative, v2.negative)

        v1.potential = 1.0
        v2.potential = 2.0

        val circuit = builder.testBuild()

        assertThrows<ElectricalSimulation.SingularLinearSystemException> {
            circuit.step()
        }
    }

    /**
     * Makes sure the solver signals when invalid results are produced.
     * */
    @Test
    fun testInvalidResults() {
        val builder = ElectricalCircuitForestBuilder()

        val r = Resistor()
        val i = CurrentSource()

        builder.add(r, i)
        builder.join(r.positive, i.positive)
        builder.join(r.negative, i.negative)

        r.resistance = ElectricalSimulation.Companion.MAX_RESISTANCE
        i.current = Double.MAX_VALUE

        val circuit = builder.testBuild()

        assertThrows<ElectricalSimulation.InvalidLinearResultsException> {
            circuit.step()
        }
    }

    /**
     * Tests a floating resistor (no loop) to make sure there is no current, potential over it.
     * */
    @Test
    fun floatingSystemTestResistor1() {
        val builder = ElectricalCircuitForestBuilder()

        val resistor = Resistor()

        builder.add(resistor)

        val circuit = builder.testBuild()

        repeat(10) {
            circuit.step()
            Assertions.assertEquals(0.0, resistor.potential)
            Assertions.assertEquals(0.0, resistor.current)
            Assertions.assertEquals(0.0, resistor.power)
        }
    }

    /**
     * Tests a floating resistor system (no loop) to make sure there is no current, potential over it.
     * */
    @Test
    fun floatingSystemTestResistor2() {
        val builder = ElectricalCircuitForestBuilder()

        val resistor1 = Resistor()
        val resistor2 = Resistor()

        builder.add(resistor1, resistor2)

        builder.join(resistor1.positive, resistor2.positive)

        val circuit = builder.testBuild()

        Assertions.assertEquals(circuit.components.size, 1)
        Assertions.assertTrue(circuit.components.first() is ResistorSystem)

        repeat(10) {
            circuit.step()
            Assertions.assertEquals(0.0, resistor1.potential)
            Assertions.assertEquals(0.0, resistor1.current)
            Assertions.assertEquals(0.0, resistor1.power)
            Assertions.assertEquals(0.0, resistor2.potential)
            Assertions.assertEquals(0.0, resistor2.current)
            Assertions.assertEquals(0.0, resistor2.power)
        }
    }

    /**
     * Tests a floating inductor-capacitor-resistor system (no loop) to make sure there is no current, potential over it.
     * */
    @Test
    fun floatingSystemTestLCR() {
        val builder = ElectricalCircuitForestBuilder()

        val inductor = Inductor()
        val capacitor = Capacitor()
        val resistor = Resistor()

        builder.add(inductor, capacitor, resistor)

        builder.join(inductor.positive, capacitor.negative)
        builder.join(capacitor.positive, resistor.negative)

        val circuit = builder.testBuild()

        repeat(10) {
            circuit.step()
            Assertions.assertEquals(0.0, inductor.potential, Double.MIN_VALUE)
            Assertions.assertEquals(0.0, inductor.current, Double.MIN_VALUE)
            Assertions.assertEquals(0.0, inductor.flux, Double.MIN_VALUE)
            Assertions.assertEquals(0.0, capacitor.potential, Double.MIN_VALUE)
            Assertions.assertEquals(0.0, capacitor.current, Double.MIN_VALUE)
            Assertions.assertEquals(0.0, capacitor.charge, Double.MIN_VALUE)
            Assertions.assertEquals(0.0, resistor.potential, Double.MIN_VALUE)
            Assertions.assertEquals(0.0, resistor.current, Double.MIN_VALUE)
            Assertions.assertEquals(0.0, resistor.power, Double.MIN_VALUE)
        }
    }

    /**
     * Tests a capacitor with incorrect initial conditions.
     * This is the [Capacitor.sanitizeState] at work.
     *
     * Without it, this test case would cause the charge and potential to grow indefinitely.
     * */
    @Test
    fun testCapacitorReconciliation() {
        val builder = ElectricalCircuitForestBuilder()

        val capacitor = Capacitor()

        builder.add(capacitor)

        val circuit = builder.testBuild()

        // Correct initial conditions:
        capacitor.charge = 1.0
        capacitor.lastPotential = 0.9 // Inconsistent state: should be 1.

        repeat(10) {
            circuit.step()

            // Reconciled by preserving charge.
            // This enforces the correct state V = 1:
            Assertions.assertEquals(1.0, capacitor.potential)
            Assertions.assertEquals(0.0, capacitor.current)
            Assertions.assertEquals(1.0, capacitor.charge)
        }
    }

    /**
     * Tests line optimization is consistent with the unoptimized circuit for a normal case circuit.
     * "testResistorSystem reference image"
     * */
    @Test
    fun testResistorSystem() {
        class TestResistorSystem(
            val builder: ElectricalCircuitForestBuilder,
            val resistors: List<Resistor>,
            val optimizable: List<Resistor>,
            val independent: List<Resistor>,
            val potentials: List<PotentialSource>,
            val currents: List<CurrentSource>
        )

        fun create() : TestResistorSystem {
            val builder = ElectricalCircuitForestBuilder()
            val rng = Random((1.0 / 137.0).toBits())

            val resistors = ArrayList<Resistor>()
            val optimizable = ArrayList<Resistor>()
            val independent = ArrayList<Resistor>()

            fun resistor() : Resistor {
                val result = Resistor()
                result.resistance = rng.nextDouble(0.001, 100.0)
                builder.add(result)
                resistors.add(result)
                return result
            }

            val r1 = resistor()
            val r2 = resistor()
            val r3 = resistor()
            val r4 = resistor()
            val r5 = resistor()
            val r6 = resistor()
            val r7 = resistor()
            val r8 = resistor()
            val r9 = resistor()

            optimizable.addAll(listOf(r1, r2, r3, r4, r5, r6, r8, r9))
            independent.add(r7)

            val v1 = PotentialSource().also { it.potential = rng.nextDouble(-100.0, 100.0) }
            val i1 = CurrentSource().also { it.current = rng.nextDouble(-100.0, 100.0) }
            builder.add(v1, i1)

            // First line:
            builder.join(r1.negative, r2.negative)
            builder.join(r2.positive, r3.negative)

            // Second line:
            builder.join(r4.negative, r5.positive)
            builder.join(r5.negative, r6.negative)

            // Third line:
            builder.join(r8.positive, r9.negative)

            // Mid node:
            builder.join(r3.positive, r4.positive)
            builder.join(r3.positive, r8.negative)
            builder.join(r3.positive, r7.positive)

            // Potential to third line:
            builder.join(r9.positive, v1.positive)

            // Current to independent:
            builder.join(r7.negative, i1.positive)

            // Build N1:
            builder.join(v1.negative, r1.positive)
            builder.join(r1.positive, i1.negative)

            // Build N2:
            builder.join(v1.negative, r6.positive)
            builder.join(r6.positive, i1.negative)

            // All done
            return TestResistorSystem(
                builder,
                resistors, optimizable, independent,
                listOf(v1), listOf(i1)
            )
        }

        val optimizedData = create()
        val unoptimizedData = create()

        val optimizedCircuit = optimizedData.builder.testBuild(true)
        val unoptimizedCircuit = unoptimizedData.builder.testBuild(false)

        optimizedData.optimizable.forEach {
            Assertions.assertTrue(it.isInOptimizedSystem)
        }

        optimizedData.independent.forEach {
            Assertions.assertTrue(!it.isInOptimizedSystem)
        }

        unoptimizedData.resistors.forEach {
            Assertions.assertTrue(!it.isInOptimizedSystem)
        }

        repeat(10) {
            optimizedCircuit.step()
            unoptimizedCircuit.step()

            for(i in 0 until optimizedData.resistors.size) {
                val a = optimizedData.resistors[i]
                val b = unoptimizedData.resistors[i]
                Assertions.assertEquals(a.potential, b.potential, 1e-5)
                Assertions.assertEquals(a.current, b.current, 1e-5)
                Assertions.assertEquals(a.power, b.power, 1e-5)
            }

            for (i in 0 until optimizedData.potentials.size) {
                val a = optimizedData.potentials[i]
                val b = unoptimizedData.potentials[i]
                Assertions.assertEquals(a.current, b.current, 1e-5)
                Assertions.assertEquals(a.power, b.power, 1e-5)
            }

            for (i in 0 until optimizedData.currents.size) {
                val a = optimizedData.currents[i]
                val b = unoptimizedData.currents[i]
                Assertions.assertEquals(a.potential, b.potential, 1e-5)
                Assertions.assertEquals(a.power, b.power, 1e-5)
            }

            val resistanceFactors = optimizedData.resistors.map {
                Random.Default.nextDouble(0.9, 1.1)
            }

            val potentialFactors = optimizedData.potentials.map {
                Random.Default.nextDouble(0.9, 1.1)
            }

            val currentFactors = optimizedData.currents.map {
                Random.Default.nextDouble(0.9, 1.1)
            }

            for(i in 0 until optimizedData.resistors.size) {
                optimizedData.resistors[i].resistance *= resistanceFactors[i]
                unoptimizedData.resistors[i].resistance *= resistanceFactors[i]
            }

            for(i in 0 until optimizedData.potentials.size) {
                optimizedData.potentials[i].potential *= potentialFactors[i]
                unoptimizedData.potentials[i].potential *= potentialFactors[i]
            }

            for(i in 0 until optimizedData.currents.size) {
                optimizedData.currents[i].current *= currentFactors[i]
                unoptimizedData.currents[i].current *= currentFactors[i]
            }
        }
    }

    /**
     * Ensures the line optimizer can handle a cycle made of 2 resistors in parallel.
     * */
    @Test
    fun testResistorSystemEdgeCase1() {
        val builder = ElectricalCircuitForestBuilder()

        val r1 = Resistor()
        val r2 = Resistor()

        builder.add(r1, r2)

        builder.join(r1.positive, r2.positive)
        builder.join(r1.negative, r2.negative)

        builder.testBuild(true)

        Assertions.assertTrue(!r1.isInOptimizedSystem && !r2.isInOptimizedSystem)
    }

    /**
     * Ensures the line optimizer can handle a cycle made of 3 resistors in a loop.
     * */
    @Test
    fun testResistorSystemEdgeCase2() {
        val builder = ElectricalCircuitForestBuilder()

        val r1 = Resistor()
        val r2 = Resistor()
        val r3 = Resistor()

        builder.add(r1, r2, r3)

        builder.join(r1.positive, r2.negative)
        builder.join(r2.positive, r3.negative)
        builder.join(r3.positive, r1.negative)

        builder.testBuild(true)

        Assertions.assertTrue(!r1.isInOptimizedSystem && !r2.isInOptimizedSystem && !r3.isInOptimizedSystem)
    }

    /**
     * Ensures the line optimizer can handle a more complicated cycle case.
     * */
    @Test
    fun testResistorSystemEdgeCase3() {
        val builder = ElectricalCircuitForestBuilder()

        val r1 = Resistor()
        val r2 = Resistor()
        val r3 = Resistor()
        val r4 = Resistor()
        val r5 = Resistor()

        builder.add(r1, r2, r3, r4, r5)

        builder.join(r1.positive, r3.negative)
        builder.join(r2.positive, r4.positive)

        builder.join(r1.negative, r2.negative)
        builder.join(r1.negative, r5.negative)

        builder.join(r3.positive, r5.positive)
        builder.join(r5.positive, r4.negative)

        builder.ground(r1.negative)

        builder.testBuild(true).also {
            Assertions.assertTrue(it.components.size == 3)
        }

        Assertions.assertTrue(r1.isInOptimizedSystem && r3.isInOptimizedSystem && r1.resistorSystem == r3.resistorSystem)
        Assertions.assertTrue(r2.isInOptimizedSystem && r4.isInOptimizedSystem && r2.resistorSystem == r4.resistorSystem)
        Assertions.assertTrue(!r5.isInOptimizedSystem)
    }

    /**
     * Ensures the line optimizer doesn't accidentally optimize a line with a ground in it.
     * */
    @Test
    fun testResistorSystemGrounding() {
        val builder = ElectricalCircuitForestBuilder()

        val r1 = Resistor()
        val r2 = Resistor()
        val r3 = Resistor()
        val r4 = Resistor()

        builder.add(r1, r2, r3, r4)

        builder.join(r1.negative, r2.positive)
        builder.join(r2.negative, r3.positive)
        builder.join(r3.negative, r4.positive)

        // Generate 2 lines:
        builder.ground(r2.negative)

        val circuit = builder.testBuild(true)

        Assertions.assertTrue(circuit.components.size == 2 && circuit.components.all { it is ResistorSystem })
        Assertions.assertTrue(r1.isInOptimizedSystem && r2.isInOptimizedSystem && r1.resistorSystem == r2.resistorSystem)
        Assertions.assertTrue(r3.isInOptimizedSystem && r4.isInOptimizedSystem && r3.resistorSystem == r4.resistorSystem)
        Assertions.assertTrue(r1.resistorSystem != r3.resistorSystem)
    }

    @Test
    fun testInductorMoveSimulation() {
        val inductor = Inductor()
        inductor.inductance = 10.0

        var builder = ElectricalCircuitForestBuilder()
        builder.add(inductor)

        // Set initial flux
        inductor.flux = 1.0

        val c1 = builder.testBuild()
        c1.step()

        Assertions.assertEquals(0.0, inductor.flux, 1e-9)

        c1.destroy()

        // Rebuild the simulation
        builder = ElectricalCircuitForestBuilder()
        builder.add(inductor)

        val c2 = builder.testBuild()
        c2.step()

        // Assert that the flux state (0.0) was persisted from c1
        // and that the simulation remains stable at 0.0.
        Assertions.assertEquals(0.0, inductor.potential, 1e-9)
        Assertions.assertEquals(0.0, inductor.current, 1e-9)
        Assertions.assertEquals(0.0, inductor.flux, 1e-9)
    }

    @Test
    fun testCapacitorMoveSimulation() {
        val cap = Capacitor()

        var builder = ElectricalCircuitForestBuilder()
        builder.add(cap)

        cap.charge = 1.0

        val c1 = builder.testBuild()
        c1.step()

        Assertions.assertEquals(cap.potential, 1.0)
        Assertions.assertEquals(cap.charge, 1.0)

        c1.destroy()

        builder = ElectricalCircuitForestBuilder()
        builder.add(cap)

        val c2 = builder.testBuild()
        c2.step()

        Assertions.assertEquals(cap.potential, 1.0)
        Assertions.assertEquals(cap.charge, 1.0)
    }

    /**
     * Tests state reset on simulation change for power sources.
     * */
    @Test
    fun testPowerSourceMoveSimulation() {
        val src = PowerSource()

        var builder = ElectricalCircuitForestBuilder()
        builder.add(src)

        src.maxPotential = 32.0
        src.targetPower = 800.0

        val c1 = builder.testBuild()
        c1.step()

        Assertions.assertEquals(src.potential, src.potential)
        Assertions.assertEquals(0.0, src.power, 1e-5)

        c1.destroy()

        builder = ElectricalCircuitForestBuilder()
        builder.add(src)

        val c2 = builder.testBuild()
        c2.step()

        Assertions.assertEquals(0.0, src.power, 1e-5)
    }

    /**
     * Makes sure the PowerSource nonlinear solver fails with an error when a generator is short-circuited.
     *
     * This forces the solver to try to deliver power into a 0V load,
     * which requires infinite current and should fail to converge.
     * */
    @Test
    fun testPowerSourceGeneratorShortCircuit() {
        val builder = ElectricalCircuitForestBuilder()

        val ps = PowerSource()
        ps.targetPower = 100.0
        ps.maxPotential = 100.0

        builder.add(ps)

        builder.ground(ps.positive)
        builder.ground(ps.negative)

        val circuit = builder.testBuild()

        assertThrows<ElectricalSimulation.PowerSourceSolverException> {
            circuit.step()
        }
    }

    //#endregion

    /**
     * Tests an LC oscillator.
     */
    @Test
    fun testLC() {
        val builder = ElectricalCircuitForestBuilder()
        val inductor = Inductor()
        val capacitor = Capacitor()

        inductor.inductance = 1.0
        capacitor.capacitance = 1.0

        builder.add(inductor, capacitor)
        builder.join(inductor.positive, capacitor.negative)
        builder.join(inductor.negative, capacitor.positive)

        val circuit = builder.testBuild()

        capacitor.charge = 1.0
        capacitor.lastPotential = 1.0
        inductor.flux = 0.0

        val dt = 1.0 / 100.0
        val stepsPerPeriod = (6.28 / dt).toInt()

        val capacitorPotentials = ArrayList<Double>()
        val inductorCurrents = ArrayList<Double>()

        repeat(stepsPerPeriod * 3) {
            circuit.step()
            capacitorPotentials.add(capacitor.potential)
            inductorCurrents.add(inductor.current)
        }

        val maxCapacitorPotential = capacitorPotentials.maxOrNull()!! // Wtf?
        val minCapacitorPotential = capacitorPotentials.minOrNull()!!
        val maxInductorCurrent = inductorCurrents.maxOrNull()!!
        val minInductorCurrent = inductorCurrents.minOrNull()!!

        Assertions.assertEquals(
            1.0,
            maxCapacitorPotential,
            0.1
        )

        Assertions.assertEquals(
            -1.0,
            minCapacitorPotential,
            0.1
        )

        Assertions.assertEquals(
            1.0,
            maxInductorCurrent,
            0.1
        )

        Assertions.assertEquals(
            -1.0,
            minInductorCurrent,
            0.1,
        )

        val initialEnergy = 0.5 * capacitor.capacitance * 1.0 * 1.0
        val finalCapacitorEnergy = 0.5 * capacitor.capacitance * capacitor.potential * capacitor.potential
        val finalInductorEnergy = 0.5 * inductor.inductance * inductor.current * inductor.current
        val finalTotalEnergy = finalCapacitorEnergy + finalInductorEnergy

        Assertions.assertEquals(
            initialEnergy,
            finalTotalEnergy,
            0.1
        )
    }

    @Test
    fun testVoltageDivider() {
        val builder = ElectricalCircuitForestBuilder()

        val v1 = PotentialSource().also { it.potential = 10.0 }
        val r1 = Resistor().also { it.resistance = 1000.0 }
        val r2 = Resistor().also { it.resistance = 1000.0 }

        builder.add(v1, r1, r2)

        builder.join(v1.positive, r1.positive)
        builder.join(r1.negative, r2.positive)
        builder.join(r2.negative, v1.negative)
        builder.ground(v1.negative)

        val circuit = builder.testBuild()
        circuit.step()

        val expectedVoltage = 10.0 * (1000.0 / (1000.0 + 1000.0))
        Assertions.assertEquals(expectedVoltage, r1.negative.potential, 1e-9)
        Assertions.assertEquals(5.0, r2.potential, 1e-9)
        Assertions.assertEquals(5.0, r1.potential, 1e-9)

        val expectedCurrent = 10.0 / 2000.0
        Assertions.assertEquals(expectedCurrent, r1.current, 1e-9)
        Assertions.assertEquals(expectedCurrent, r2.current, 1e-9)
        Assertions.assertEquals(expectedCurrent, v1.current, 1e-9)
    }

    @Test
    fun testCurrentDivider() {
        val builder = ElectricalCircuitForestBuilder()

        val i1 = CurrentSource().also { it.current = 10.0 }
        val r1 = Resistor().also { it.resistance = 100.0 }
        val r2 = Resistor().also { it.resistance = 300.0 }

        builder.add(i1, r1, r2)

        builder.join(i1.positive, r1.positive)
        builder.join(i1.positive, r2.positive)
        builder.join(i1.negative, r1.negative)
        builder.join(i1.negative, r2.negative)
        builder.ground(i1.negative)

        val circuit = builder.testBuild()
        circuit.step()

        val iR1 = 10.0 * (300.0 / (100.0 + 300.0))
        val iR2 = 10.0 * (100.0 / (100.0 + 300.0))

        Assertions.assertEquals(7.5, iR1, 1e-9)
        Assertions.assertEquals(2.5, iR2, 1e-9)

        val expectedVoltage = 10.0 * (1.0 / (1.0/100.0 + 1.0/300.0))
        Assertions.assertEquals(750.0, expectedVoltage, 1e-9)
        Assertions.assertEquals(expectedVoltage, i1.potential, 1e-9)
        Assertions.assertEquals(expectedVoltage, r1.potential, 1e-9)
        Assertions.assertEquals(expectedVoltage, r2.potential, 1e-9)
    }

    @Test
    fun testRCCircuitCharging() {
        val builder = ElectricalCircuitForestBuilder()

        val v1 = PotentialSource().also { it.potential = 10.0 }
        val r1 = Resistor().also { it.resistance = 1000.0 }
        val c1 = Capacitor().also { it.capacitance = 0.001 }

        val rc = r1.resistance * c1.capacitance
        Assertions.assertEquals(1.0, rc, 1e-9)

        builder.add(v1, r1, c1)
        builder.join(v1.positive, r1.positive)
        builder.join(r1.negative, c1.positive)
        builder.join(c1.negative, v1.negative)
        builder.ground(v1.negative)

        val circuit = builder.testBuild()

        repeat(100) {
            circuit.step()
        }

        Assertions.assertEquals(6.321, c1.potential, 0.1) // Use a larger delta due to discrete steps
        Assertions.assertEquals(10.0 - c1.potential, r1.potential, 0.01)

        v1.potential = 0.0

        repeat(100) {
            circuit.step()
        }

        Assertions.assertEquals(2.325, c1.potential, 0.01)
    }

    @Test
    fun testRLCircuitCurrentRise() {
        val builder = ElectricalCircuitForestBuilder()

        val v1 = PotentialSource().also { it.potential = 10.0 }
        val r1 = Resistor().also { it.resistance = 10.0 }
        val l1 = Inductor().also { it.inductance = 10.0 }

        val tau = l1.inductance / r1.resistance
        Assertions.assertEquals(1.0, tau, 1e-9)

        builder.add(v1, r1, l1)
        builder.join(v1.positive, r1.positive)
        builder.join(r1.negative, l1.positive)
        builder.join(l1.negative, v1.negative)
        builder.ground(v1.negative)

        val circuit = builder.testBuild()

        repeat(100) {
            circuit.step()
        }

        Assertions.assertEquals(0.632, l1.current, 0.01) // Use a larger delta
        Assertions.assertEquals(0.632, r1.current, 0.01)
        Assertions.assertEquals(0.632, v1.current, 0.01)

        val vR = l1.current * r1.resistance
        val vL = v1.potential - vR
        Assertions.assertEquals(vR, r1.potential, 0.01)
        Assertions.assertEquals(vL, l1.potential, 0.01)
    }

    /**
     * Makes sure the power source respects the **potential constraint** in **generator mode**, with a **passive load**.
     * */
    @Test
    fun testPowerSourceGeneratorPotentialConstraintPassiveLoad() {
        val builder = ElectricalCircuitForestBuilder()

        val ps = PowerSource()
        val load = Resistor()

        builder.add(ps, load)
        builder.join(ps.positive, load.positive)
        builder.join(ps.negative, load.negative)

        ps.maxPotential = 100.0
        ps.targetPower = 1000.0
        load.resistance = 1e5 // So the max power isn't reached

        val circuit = builder.testBuild()

        repeat(10) {
            circuit.step()

            Assertions.assertTrue(ps.power > 0.0 && ps.potential < ps.maxPotential) // blend region
            Assertions.assertTrue(load.power > 0.0)
        }
    }

    /**
     * Validates the common behavior of the power source (see the documentation in [PowerSource]):
     * - open-circuit when [PowerSource.targetPower] is zero
     * */
    @Test
    fun testPowerSourceCommonBehavior() {
        val builder = ElectricalCircuitForestBuilder()

        val powerSource = PowerSource()
        val potentialSource = PotentialSource()

        builder.add(powerSource, potentialSource)

        builder.join(powerSource.positive, potentialSource.positive)
        builder.join(powerSource.negative, potentialSource.negative)

        powerSource.targetPower = 0.0
        potentialSource.potential = 10.0

        val circuit = builder.testBuild()

        fun verify() {
            repeat(5) {
                circuit.step()

                Assertions.assertEquals(0.0, powerSource.power, circuit.powerSourceResidualTolerance)
                Assertions.assertEquals(potentialSource.potential, powerSource.potential, 0.1)
                Assertions.assertEquals(0.0, potentialSource.power, circuit.powerSourceResidualTolerance)
                Assertions.assertTrue(circuit.lastPowerSourceIterationCount < 3)
            }
        }

        potentialSource.potential = 0.0
        verify()

        potentialSource.potential = 0.0001
        verify()

        potentialSource.potential = 10000.0
        verify()

        potentialSource.potential = -0.001
        verify()

        potentialSource.potential = -1.0
        verify()

        potentialSource.potential = -10.0
        verify()

        potentialSource.potential = -1000.0
        verify()
    }

    /**
     * Tests the behavior of the power source **in generator mode** (see the documentation in [PowerSource]):
     * - the circuit forcing power into the source
     * - the source generating power with a positive potential over it
     * - the source generating power with a negative potential over it
     * */
    @Test
    fun testPowerSourceGeneratorReverseForwardForward() {
        val builder = ElectricalCircuitForestBuilder()

        val powerSource = PowerSource()
        val potentialSource = PotentialSource()

        builder.add(powerSource, potentialSource)

        builder.join(powerSource.positive, potentialSource.positive)
        builder.join(powerSource.negative, potentialSource.negative)

        powerSource.targetPower = 10.0
        powerSource.maxPotential = 10.0

        potentialSource.potential = 11.0

        val circuit = builder.testBuild()

        repeat(5) {
            circuit.step()

            Assertions.assertEquals(-1.0, powerSource.power, 1e-6) // One watt is being forced into the device
            Assertions.assertTrue(circuit.lastPowerSourceIterationCount < 3)
        }

        potentialSource.potential = 9.0

        repeat(5) {
            circuit.step()

            Assertions.assertEquals(10.0, powerSource.power, 1e-6) // Creating a current through the potential source
            Assertions.assertEquals(-10.0, potentialSource.power, 1e-6)
            Assertions.assertTrue(circuit.lastPowerSourceIterationCount < 3)
        }

        potentialSource.potential = -10.0

        repeat(5) {
            circuit.step()

            Assertions.assertEquals(10.0, powerSource.power, 1e-6) // Creating a current through the potential source
            Assertions.assertEquals(-10.0, potentialSource.power, 1e-6)
            Assertions.assertTrue(circuit.lastPowerSourceIterationCount < 3)
        }

        potentialSource.potential = -100.0

        repeat(5) {
            circuit.step()

            Assertions.assertEquals(10.0, powerSource.power, 1e-6) // Creating a current through the potential source
            Assertions.assertEquals(-10.0, potentialSource.power, 1e-6)
            Assertions.assertTrue(circuit.lastPowerSourceIterationCount < 3)
        }
    }

    /**
     * Tests the most basic case for a power consumer.
     * */
    @Test
    fun testPowerConsumerBasic() {
        val builder = ElectricalCircuitForestBuilder()

        val vs = PotentialSource().also { it.potential = 20.0 } // 20V source
        val pc = PowerConsumer()

        builder.add(vs, pc)
        builder.join(vs.positive, pc.positive)
        builder.join(vs.negative, pc.negative)

        pc.targetPower = 50.0 // Request 50W
        pc.minEquivalentResistance = 0.1

        val circuit = builder.testBuild()

        circuit.step()

        Assertions.assertEquals(20.0, pc.potential, 0.1)
        Assertions.assertEquals(50.0, pc.power, 0.1)

        // Check that VS is supplying 50W
        Assertions.assertEquals(50.0, vs.power, 0.1)
        Assertions.assertEquals(2.5, vs.current, 0.1)
    }

    /**
     * Tests the equivalent resistance constraint for the power consumer.
     * */
    @Test
    fun testPowerConsumerConstraint() {
        val builder = ElectricalCircuitForestBuilder()

        val vs = PotentialSource().also { it.potential = 10.0 }
        val pc = PowerConsumer()

        builder.add(vs, pc)
        builder.join(vs.positive, pc.positive)
        builder.join(vs.negative, pc.negative)

        pc.targetPower = 500.0

        pc.minEquivalentResistance = 1.0

        val circuit = builder.testBuild()

        circuit.step()

        Assertions.assertEquals(10.0, pc.potential, 1e-6)
        Assertions.assertEquals(100.0, pc.power, 1e-5)
        Assertions.assertEquals(100.0, vs.power, 1e-5)

        pc.minEquivalentResistance = 0.1
        circuit.step()

        Assertions.assertEquals(10.0, pc.potential, 1e-6)
        Assertions.assertEquals(500.0, pc.power, 1e-5)
        Assertions.assertEquals(500.0, vs.power, 1e-5)
    }

    /**
     * Makes sure that a power consumer with zero target power is an open circuit.
     * */
    @Test
    fun testPowerConsumerZeroTarget() {
        val builder = ElectricalCircuitForestBuilder()

        val vs = PotentialSource().also { it.potential = 10.0 }
        val pc = PowerConsumer()

        builder.add(vs, pc)
        builder.join(vs.positive, pc.positive)
        builder.join(vs.negative, pc.negative)

        pc.targetPower = 0.0

        val circuit = builder.testBuild()

        circuit.step()

        Assertions.assertEquals(10.0, pc.potential, 0.1)
        Assertions.assertEquals(0.0, pc.power, 0.1)
        Assertions.assertEquals(0.0, vs.power, 0.1)
        Assertions.assertEquals(0.0, vs.current, 0.1)
    }

    /**
     * Tests transfer between two power devices.
     * */
    @Test
    fun testPowerSourceToConsumerStable() {
        val builder = ElectricalCircuitForestBuilder()

        val pg = PowerSource().also {
            it.maxPotential = 100.0
        }

        val pc = PowerConsumer().also {
            it.minEquivalentResistance = 0.5
        }

        val r = Resistor().also { it.resistance = 1.0 }

        builder.add(pg, pc, r)
        builder.join(pg.positive, r.positive)
        builder.join(r.negative, pc.positive)
        builder.join(pc.negative, pg.negative)

        val circuit = builder.testBuild()

        repeat(1000) {
            if(it < 124) {
                return@repeat
            }

            val genPower = 1000.0 * sin(it * circuit.dt).pow(2)
            val consPower = 1000.0 * cos(it * circuit.dt).pow(2)

            pg.targetPower = genPower
            pc.targetPower = consPower

            try {
                circuit.step()
            }
            catch (e: ElectricalSimulation.PowerSourceSolverException) {
                println("src targetPower: ${pg.targetPower}, src power: ${pg.power}")
                println("sink targetPower: ${pc.targetPower}, sink power: ${pc.power}")
                fail(e)
            }
            catch (e: Exception) {
                fail(e)
            }

            val dissipated = r.power
            val generated = pg.power
            val consumed = pc.power

            Assertions.assertEquals(generated, dissipated + consumed, 0.1)
        }
    }

    /**
     * Tests basic power transfer between a few power devices.
     * */
    @Test
    fun testMultiSourceMultiConsumerStable() {
        val builder = ElectricalCircuitForestBuilder()

        val ps1 = PowerSource().also {
            it.maxPotential = 200.0
        }

        val ps2 = PowerSource().also {
            it.maxPotential = 200.0
        }

        val pc1 = PowerConsumer().also {
            it.minEquivalentResistance = 0.5
        }

        val pc2 = PowerConsumer().also {
            it.minEquivalentResistance = 0.5
        }

        val r1 = Resistor().also { it.resistance = 2.0 }
        val r2 = Resistor().also { it.resistance = 5.0 }
        val r3 = Resistor().also { it.resistance = 2.0 }

        builder.add(ps1, ps2, pc1, pc2, r1, r2, r3)

        builder.join(ps1.positive, r1.positive)
        builder.join(ps1.positive, r2.positive)

        val builder2 = ElectricalCircuitForestBuilder()
        builder2.add(ps1, ps2, pc1, pc2, r1, r2, r3)

        builder2.join(ps1.positive, r1.positive)
        builder2.join(r1.negative, pc1.positive)

        builder2.join(ps2.positive, r3.positive)
        builder2.join(r3.negative, pc2.positive)

        builder2.join(r1.negative, r2.positive)
        builder2.join(r3.negative, r2.negative)

        builder2.ground(ps1.negative)
        builder2.ground(ps2.negative)
        builder2.ground(pc1.negative)
        builder2.ground(pc2.negative)

        val circuit = builder2.testBuild()

        val steps = 10000

        repeat(steps) { step ->
            // Very-varying system:
            ps1.targetPower = 1000.0 * sin(step * circuit.dt * 0.5).pow(2) + 10.0 // Slow
            ps2.targetPower = 800.0 * sin(step * circuit.dt * 1.0).pow(2) + 10.0 // Medium

            pc1.targetPower = 600.0 * cos(step * circuit.dt * 0.7).pow(2) + 5.0 // Med-Slow
            pc2.targetPower = 500.0 * cos(step * circuit.dt * 1.2).pow(2) + 5.0 // Fast

            try {
                circuit.step()
            } catch (e: Exception) {
                fail("Solver exception: $e")
            }

            val generated = ps1.power + ps2.power
            val consumed = pc1.power + pc2.power
            val dissipated = r1.power + r2.power + r3.power

            if (ps1.power < -0.01 || ps2.power < -0.01) {
                fail("Step $step: PowerSource is consuming power")
            }

            if (pc1.power < -0.01 || pc2.power < -0.01) {
                fail("Step $step: PowerConsumer is generating power")
            }

            Assertions.assertEquals(generated, consumed + dissipated, 0.1, "Power balance failed at step $step")
        }
    }

    /**
     * Tests a failure point observed in-game (with the previous solver).
     * */
    @Test
    fun powerConsumerCapacitorFeedbackTest1() {
        val builder = ElectricalCircuitForestBuilder()

        val cons = PowerConsumer()

        val cap = Capacitor()
        cap.capacitance = 1e-4
        cap.charge = 100.0 * cap.capacitance // 100 volt

        val initialEnergy = cap.internalEnergy

        builder.add(cons)
        builder.add(cap)

        builder.ground(cons.negative)
        builder.join(cap.negative, cons.negative)
        builder.join(cons.positive, cap.positive)

        val c = builder.build(1.0 / 100.0, false, ElectricalSimulation.ConstructionOptions(null)).solvers.first()

        var lostEnergy = 0.0

        repeat(50) { step ->
            val t = step * c.dt

            cons.targetPower = (sin(t * 20.0).pow(2) * 500.0) + 10.0

            try {
                c.step()
            } catch (e: Exception) {
                println("STEP $step: Solver failed: $e")
                return
            }

            val power = cons.power
            val potential = cons.potential

            if (power.isNaN() || power.isInfinite() || potential.isNaN() || potential.isInfinite()) {
                fail("Invalid output")
            }

            lostEnergy += power * c.dt + cap.lostEnergy
        }

        Assertions.assertTrue(cap.potential < 0.1, "The capacitor's energy wasn't bled off by the consumer")
        Assertions.assertEquals(
            lostEnergy,
            initialEnergy,
            1e-8
        ) // In the test, it was equal within floating point error
    }

    /**
     * Tests a bigger power device system.
     * */
    @Test
    fun testFullSystemDynamicStress() {
        repeat(10) {
            val builder = ElectricalCircuitForestBuilder()

            // Bus 1 (Main Grid)
            val pg1 = PowerSource().also {
                it.maxPotential = 100.0
            }

            val pc1 = PowerConsumer().also {
                it.minEquivalentResistance = 0.1
            }

            val cap1 = Capacitor().also { it.capacitance = 1e-3 }
            val ind1 = Inductor().also { it.inductance = 1e-2 }

            val vs = PotentialSource().also { it.potential = 24.0 }
            val rBatt = Resistor().also { it.resistance = 0.05 }

            val pc2 = PowerConsumer().also {
                it.minEquivalentResistance = 0.5
            }
            val cap2 = Capacitor().also { it.capacitance = 1e-4 }
            val ind2 = Inductor().also { it.inductance = 1e-3 }

            // Connector (DC-DC Charger from Bus 1 to Bus 2)
            val pg2 = PowerSource().also {
                it.maxPotential = 50.0 // Can boost voltage
            }

            builder.add(pg1, pc1, cap1, ind1, vs, rBatt, pc2, cap2, ind2, pg2)

            // Connect Bus 1
            builder.join(pg1.positive, ind1.positive)
            val bus1 = ind1.negative

            // pc1 and cap1 connect to bus1
            builder.join(bus1, pc1.positive, cap1.positive)

            // pg2 draws from bus1
            builder.join(bus1, pg2.negative)

            // Connect Bus 2
            builder.join(vs.positive, rBatt.positive)
            builder.join(rBatt.negative, ind2.positive)
            val bus2 = ind2.negative

            builder.join(bus2, pc2.positive, cap2.positive)

            builder.join(bus2, pg2.positive)

            builder.ground(pg1.negative, pc1.negative, cap1.negative, vs.negative, pc2.negative, cap2.negative)

            val circuit = builder.testBuild()
            val dt = circuit.dt

            var totalOuterIterations = 0
            var totalSourceIterations = 0
            var forceJacobianResets = 0

            var stepTimeTotal = 0.0

            repeat(5000) { step ->
                val t = step * dt

                pg1.targetPower = 5000.0 + 2500.0 * sin(t * 5.0)
                pc1.targetPower = 4000.0 + 3000.0 * cos(t * 3.0)
                pc2.targetPower = 200.0 + 150.0 * sin(t * 10.0)

                val batteryVoltage = pc2.positive.potential
                pg2.targetPower = when {
                    batteryVoltage < 23.8 -> 500.0
                    batteryVoltage > 24.2 -> 0.0
                    else -> 100.0
                }

                try {
                    val testTime = measureDuration {
                        circuit.step()
                    }

                    if(step > 100) {
                        stepTimeTotal += !testTime
                    }
                } catch (e: Exception) {
                    fail("Solver failed at step $step with t=$t: $e")
                }

                totalOuterIterations += circuit.lastOuterLoopIterations
                totalSourceIterations += circuit.lastPowerSourceIterationCount
                forceJacobianResets += circuit.lastPowerSourceForceJacobianResets

                if (step < 20) {
                    return@repeat
                }

                Assertions.assertTrue(pc1.power >= -0.1)
                Assertions.assertTrue(pc2.power >= -0.1)
                Assertions.assertTrue(pg1.power >= -0.1)
                Assertions.assertTrue(pg2.power >= -0.1) {
                    ""
                }

                val pGen1 = pg1.power
                val pGen2 = pg2.power
                val pVs = vs.power

                val pCons1 = pc1.power
                val pCons2 = pc2.power

                val pRBatt = rBatt.power

                val pCap1 = cap1.power
                val pCap2 = cap2.power
                val pInd1 = ind1.power
                val pInd2 = ind2.power

                val totalPowerGenerated = pGen1 + pGen2 + pVs
                val totalPowerConsumed = pCons1 + pCons2 + pRBatt + pCap1 + pCap2 + pInd1 + pInd2

                Assertions.assertEquals(
                    totalPowerGenerated,
                    totalPowerConsumed,
                    0.1
                )
            }

            println("Stress test: Outer Iterations: $totalOuterIterations, Source Iterations: $totalSourceIterations, Jacobian Force: $forceJacobianResets, Step Time: ${stepTimeTotal.rounded(4)}")
        }
    }

    @Test
    fun testLargeScalePowerSourcePerformance() {
        repeat(5) {
            val builder = ElectricalCircuitForestBuilder()
            val nTest = 50 // Consumers and Sources

            println("Building large-scale performance test with N=$nTest...")

            val sources = Array(nTest) {
                PowerSource().also {
                    it.maxPotential = 400.0
                    it.characteristicResistance = 0.1
                }
            }

            val consumers = Array(nTest) {
                PowerConsumer().also {
                    it.minEquivalentResistance = 0.1
                    it.characteristicResistance = 0.1
                }
            }

            val vsGr = PotentialSource().also { it.potential = 200.0 }
            val rGr = Resistor().also { it.resistance = 0.1 }
            val rLnk = Resistor().also { it.resistance = 0.5 }

            builder.add(vsGr, rGr, rLnk)
            builder.add(*sources)
            builder.add(*consumers)

            val genBusPin = rGr.negative
            val loadBusPin = rLnk.negative

            builder.join(vsGr.positive, rGr.positive)
            builder.join(genBusPin, rLnk.positive)

            sources.forEach {
                builder.join(genBusPin, it.positive)
                builder.ground(it.negative)
            }

            consumers.forEach {
                builder.join(loadBusPin, it.positive)
                builder.ground(it.negative)
            }

            builder.ground(vsGr.negative)

            val circuit = builder.testBuild()

            println("Running $nTest-source/$nTest-consumer simulation...")

            val steps = 200
            val dt = circuit.dt
            val warmup = 20

            var totalOuterLoops = 0
            var totalPsIterations = 0
            var totalPsResets = 0
            var totalSolveTime = 0.0

            repeat(steps) { step ->
                (0 until nTest).forEach { i ->
                    sources[i].targetPower = 1000.0 * sin(step * dt * 0.2 + i * 0.1).pow(2) + 10.0
                    consumers[i].targetPower = 1000.0 * cos(step * dt * 0.2 + i * 0.1).pow(2) + 5.0
                }

                try {
                    val solveTime = measureDuration {
                        circuit.step()
                    }
                    if (step > warmup) {
                        totalSolveTime += !solveTime
                    }
                } catch (e: Exception) {
                    println("Test failed at step $step")
                    fail("Solver exception: $e")
                }

                if (step > warmup) {
                    totalOuterLoops += circuit.lastOuterLoopIterations
                    totalPsIterations += circuit.lastPowerSourceIterationCount
                    totalPsResets += circuit.lastPowerSourceForceJacobianResets // Add this

                    Assertions.assertTrue(sources[0].power > -0.1, "Step $step: PS is consuming power")
                    Assertions.assertTrue(consumers[0].power > -0.1, "Step $step: PC is generating power")

                    Assertions.assertTrue(
                        circuit.lastOuterLoopIterations < circuit.powerDeviceMaxOuterLoopIterationsConsumerViolation,
                        "Solver thrashed at step $step (Outer Loop)"
                    )
                }
            }

            val runSteps = steps - warmup

            println("$nTest-Source Performance Report")
            println("  Successfully completed $steps steps.")
            println("  Total Solve Time (after warmup): ${totalSolveTime.rounded(4)}")
            println("  Average Step Time: ${(totalSolveTime / runSteps * 1000.0).rounded(4)}")
            println("  Average Outer Loops per Step: ${(totalOuterLoops.toDouble() / runSteps).rounded()}")
            println("  Average PS Iterations per Step: ${(totalPsIterations.toDouble() / runSteps).rounded()}")
        }
    }
}
