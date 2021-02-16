package org.eln2.sim.electrical.mna.component

import org.eln2.debug.dprintln
import org.eln2.sim.electrical.mna.Circuit

/**
 * Implements a simple, static resistor.
 *
 * The most important field is arguably [resistance]; updating this value will result in [Circuit.matrixChanged].
 */
open class Resistor : Port() {
    override var name: String = "r"
    override val imageName = "resistor"

    /**
     * The resistance of this resistor, in Ohms.
     *
     * Setting this will cause [Circuit.factorMatrix] to be called on the next step or substep.
     */
    open var resistance: Double = 1.0
        set(value) {
            if(isInCircuit) {
                // Remove our contribution to the matrix (using a negative resistance... should work)
                field = -field
                stamp()
            }

            // Add our new contribution
            field = value
            if(isInCircuit) stamp()
        }

    /**
     * Returns the current through this resistor as a function of its potential and resistance, in Amperes.
     */
    open val current: Double
        get() = potential / resistance

    /**
     * Returns the power dissipated by this resistor, as a function of its current and potential, in Watts.
     */
    open val power: Double
        get() = current * potential

    override fun detail(): String {
        return "[resistor $name: ${potential}v, ${current}A, ${resistance}Ω, ${power}W]"
    }

    override fun stamp() {
        dprintln("pos=$pos neg=$neg r=$resistance")
        // We have to guard here specifically against stamp() being called out of context from the resistance setter above.
        if(pos != null && neg != null) pos!!.stampResistor(neg!!, resistance)
    }
}
