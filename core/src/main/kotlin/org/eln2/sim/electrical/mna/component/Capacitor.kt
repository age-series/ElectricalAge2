package org.eln2.sim.electrical.mna.component

import org.eln2.debug.dprintln

/**
 * A capacitor.
 *
 * Capacitors are defined by a characteristic of "mutual capacitance", which (for two separate conductors sharing an E-field) is defined as the charge per unit potential. Since current is the derivative of charge, current in any one direction causes an increase in potential across the capacitor, often referred to as "charging" when the current is of the same sign as the prevailing potential, and "discharging" when it is opposite.
 *
 * Thus, as an implementation detail, one of the two out-of-phase components must be stored--either the present charge, or the ideal potential. This implementation stores the ideal potential as [idealU]. The other can be calculated directly using [capacitance].
 *
 * This reactive component is simulated using a Norton system, as designed by Falstad. It is not "non-linear", however, and does not need any substeps to compute. It does, however, need to know the timescale of the simulation steps. Changing the simulation timestep dynamically can cause performance problems, however, and is best avoided.
 */
open class Capacitor : Port() {
    override var name: String = "c"

    /**
     * Capacitance, in Farads (Coulombs / Volt)
     */
    var capacitance: Double = 1e-5
        set(value) {
            if(isInCircuit) unstamp()
            field = value
            if(isInCircuit) stamp()
        }

    /**
     * Energy Stored, in Joules
     */
    @Suppress("MemberVisibilityCanBePrivate")
    val energy: Double
        // https://wikimedia.org/api/rest_v1/media/math/render/svg/2d44d092abb138877538fad86bc77e18b26fb1bc
        get() = 0.5 * capacitance * potential * potential

    /**
     * The simulation timestep in seconds.
     *
     * This is set in [preStep], but the value is unfortunately not available during [stamp]; thus, it may be slightly out of date when [step] is actually called.
     */
    var ts: Double = 0.05  // A safe default
        set(value) {
            if(isInCircuit) unstamp()
            field = value
            if(isInCircuit) stamp()
        }

    /**
     * The "equivalent resistance" of the Norton system, in Ohms.
     */
    private val eqR: Double
        get() = ts / capacitance

    /**
     * Current across the device (as a whole), in Amps. See [i] for the current sourced by the Norton system.
     */
    val current: Double
        get() = if (eqR > 0) {
            potential / eqR + i
        } else 0.0

    /**
     * The current, in Amperes, presently sourced by this Norton system.
     */
    internal var i: Double = 0.0
        set(value) {
            if (isInCircuit && pos != null && neg != null)
                circuit!!.stampCurrentSource(pos!!.index, neg!!.index, value - field)
            field = value
        }

    /**
     * The current ideal potential across nodes, signed [pos] - [neg] in Volts, a function of the capacitance and the charge (integration of previous currents).
     */
    var idealU: Double = 0.0

    override fun detail(): String {
        return "[capacitor $name: ${potential}v, ${i}A, ${capacitance}F, ${energy}J]"
    }

    override fun preStep(dt: Double) {
        if(ts != dt) ts = dt  // May cause conductivity change/matrix solve step--avoid this if possible
        i = -idealU / eqR
        dprintln("C.preS: i=$i eqR=$eqR idealU=$idealU")
    }


    override fun postStep(dt: Double) {
        idealU = potential
        dprintln("C.postS: potential=$potential (-> idealU)")
    }

    override fun stamp() {
        if(pos != null && neg != null) pos!!.stampResistor(neg!!, eqR)
    }
    
    protected fun unstamp() {
        if(pos != null && neg != null) pos!!.stampResistor(neg!!, -eqR)
    }
}
