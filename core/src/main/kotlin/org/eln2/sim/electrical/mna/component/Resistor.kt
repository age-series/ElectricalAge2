package org.eln2.sim.electrical.mna.component

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
            // Remove our contribution to the matrix (using a negative resistance... should work)
            field = -field
            stamp()

            // Add our new contribution
            field = value
            stamp()
        }

    /**
     * Returns the current through this resistor as a function of its potential and resistance, in Amperes.
     */
    open val current: Double
        get() = potential / resistance

    /**
     * Returns the power dissipated by this resistor, as a function of its current and potential, in Watts.
     */
    open val p: Double
        get() = current * potential

    override fun detail(): String {
        return "[resistor r:$resistance]"
    }

    override fun stamp() {
        if (!isInCircuit) return
        node(0).stampResistor(node(1), resistance)
    }
}
