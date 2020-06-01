package org.eln2.sim.electrical.mna.component

import org.eln2.debug.dprintln

/**
 * A capacitor.
 *
 * Capacitors are defined by a characteristic of "mutual capacitance", which (for two separate conductors sharing an E-field) is defined as the charge per unit potential. Since current is the derivative of charge, current in any one direction causes an increase in potential across the capacitor, often referred to as "charging" when the current is of the same sign as the prevailing potential, and "discharging" when it is opposite.
 *
 * Thus, as an implementation detail, one of the two out-of-phase components must be stored--either the present charge, or the ideal potential. This implementation stores the ideal potential as [idealU].
 *
 * This reactive component is simulated using a Norton system, as designed by Falstad. It is not "non-linear", however, and does not need any substeps to compute. It does, however, need to know the timescale of the simulation steps.
 */
open class Capacitor : Port() {
    override var name: String = "c"

    /**
     * The current, in Amperes, presently sourced by this Norton system.
     */
    internal var i: Double = 0.0
        set(value) {
            if (isInCircuit)
                circuit!!.stampCurrentSource(pos.index, neg.index, value - field)
            field = value
        }

    /**
     * Capacitance, in Farads (Coulombs / Volt)
     */
    var capacitance: Double = 1e-5

    /**
     * The simulation timestep in seconds.
     *
     * This is set in [preStep], but the value is unfortunately not available during [stamp]; thus, it may be slightly out of date when [step] is actually called.
     */
    var ts: Double = 0.05  // A safe default

    /**
     * The "equivalent resistance" of the Norton system, in Ohms.
     */
    private val eqR: Double
        get() = ts / capacitance

    /**
     * The current, in Amperes, presently sourced by this Norton system.
     */
    internal var current: Double = 0.0
        set(value) {
            if (isInCircuit)
                circuit!!.stampCurrentSource(pos.index, neg.index, value - field)
            field = value
        }

    /**
     * The current ideal potential across nodes, signed [pos] - [neg] in Volts, a function of the capacitance and the charge (integration of previous currents).
     */
    var idealU: Double = 0.0

    override fun detail(): String {
        return "[capacitor c:$capacitance]"
    }

    override fun preStep(dt: Double) {
        ts = dt
        current = -idealU / eqR
        dprintln("C.preS: i=$current eqR=$eqR idealU=$idealU")
    }


    override fun postStep(dt: Double) {
        idealU = potential
        dprintln("C.postS: potential=$potential (-> idealU)")
    }

    override fun stamp() {
        pos.stampResistor(neg, eqR)
    }
}
