package org.eln2.apps.electrical

import org.eln2.debug.mnaPrintln
import org.eln2.sim.electrical.mna.Circuit
import org.eln2.sim.electrical.mna.NEGATIVE
import org.eln2.sim.electrical.mna.POSITIVE
import org.eln2.sim.electrical.mna.component.Capacitor
import org.eln2.sim.electrical.mna.component.CurrentSource
import org.eln2.sim.electrical.mna.component.DiodeData
import org.eln2.sim.electrical.mna.component.IdealDiode
import org.eln2.sim.electrical.mna.component.Inductor
import org.eln2.sim.electrical.mna.component.RealisticDiode
import org.eln2.sim.electrical.mna.component.Resistor
import org.eln2.sim.electrical.mna.component.VoltageSource

/**
 * This function is created to make it easy for people to play with the Eln2 simulator, and all of the available components.
 *
 * Have fun, and if you have questions, ask in the Discord: https://discord.gg/YjK2JAD
 */
fun main() {
    val circuit = Circuit()

    // Step 1: Create components to use, set their values. All values are in SI base units (volts, amps, ohms, henries, farads, etc).

    // Resistors
    val r1 = Resistor()
    r1.resistance = 1.0
    val r2 = Resistor()
    r2.resistance = 2.0
    val r3 = Resistor()
    r3.resistance = 3.0

    // Capacitors
    val c1 = Capacitor()
    c1.capacitance = 1.0
    val c2 = Capacitor()
    c2.capacitance = 2.0

    // Inductors
    val i1 = Inductor()
    i1.inductance = 1.0
    val i2 = Inductor()
    i2.inductance = 2.0

    // Voltage Sources (think ideal battery)
    val vs1 = VoltageSource()
    vs1.potential = 1.0
    val vs2 = VoltageSource()
    vs2.potential = 2.0

    // Current Sources (A bit complex)
    val cs1 = CurrentSource()
    cs1.current = 1.0
    val cs2 = CurrentSource()
    cs2.current = 2.0

    // A basic diode
    val di1 = IdealDiode()

    /*
    A realistic performing diode.

    Values can be one of the following:
    * spice-default
    * falstad-default
    * falstad-zener
    * falstad-old-led
    * falstad-led
    * schottky-1N5711
    * schottky-1N5712
    * germanium-1N34
    * 1N4004
    * 1N3891
    * switching-1N4148
     */
    val di2 = RealisticDiode(DiodeData.diodes.getOrElse("1N4004") { throw Exception("Could not find diode data") })

    // Step 2: Add the components you want to use in the circuit

    circuit.add(r1, i1, vs1)

    // Step 3: Connect the components together

    vs1.connect(POSITIVE, r1, POSITIVE)
    r1.connect(NEGATIVE, i1, POSITIVE)
    i1.connect(NEGATIVE, vs1, NEGATIVE)
    vs1.ground(NEGATIVE)

    // Step 4: Step the simulation for dt seconds of time.

    if (!circuit.step(0.05)) {
        println("Circuit Step Failed")
    }

    // Step 5: Print out the MNA matrix to see the results.

    mnaPrintln(circuit)

    /*
    The output of that function is not easy to parse, but you can ask components what their current, voltage, etc are.

    To understand the matrix better, see this: https://lpsa.swarthmore.edu/Systems/Electrical/mna/MNA2.html#Observations_about_MNA
     */

    // It may be easier to see what is going on by printing the values of the components, which should all be populated.
    println("R1 Current: ${r1.current} Amps")
}
