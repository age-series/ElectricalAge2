package org.eln2.sim.electrical.mna.component

/**
 * An inductor.
 *
 * Inductors are defined by a characteristic of "self-inductance", wherein magnetic flux (change in magnetic field) induces an electric field, quantified as magnetic flux per unit of current. Since induced voltage is proportional to magnetic flux, we can take the derivative of current with respect to time and determine instantaneous voltage.
 *
 * Of the two out-of-phase values that could be stored (the derivative of current, or the magnetic flux), this implementation uses the magnetic flux [phi].
 *
 * This reactive component is simulated using a Norton system, as designed by Falstad. It is not "non-linear", however, and does not need any substeps to compute. It does, however, need to know the timescale of the simulation steps. Changing the simulation timestep dynamically can cause performance problems, however, and is best avoided.
 */
open class Inductor : Port() {
    override var name: String = "l"

    /**
     * Self-inductance in Henries, singular Henry (Volts / Ampere).
     */
    var inductance: Double = 0.0
        set(value) {
            if(isInCircuit) unstamp()
            field = value
            if(isInCircuit) stamp()
        }

    /**
     * The simulation timestep in seconds.
     *
     * This is set in [preStep], but the value is unfortunately not available during [stamp]; thus, it may be slightly out of date when [step] is actually called.
     */
    var ts: Double = 0.05 // A safe default
        set(value) {
            if(isInCircuit) unstamp()
            field = value
            if(isInCircuit) stamp()
        }

    /**
     * The "equivalent resistance" of the Norton system, in Ohms.
     */
    private val eqR: Double
        get() = inductance / ts

    /**
     * The current, in Amperes, presently sourced by this Norton system. See [current] for the overall current.
     */
    internal var i: Double = 0.0
        set(value) {
            if (isInCircuit && pos != null && neg != null)
                circuit!!.stampCurrentSource(pos!!.index, neg!!.index, value - field)
            field = value
        }

    /**
     * Energy Stored, in Joules
     */
    @Suppress("MemberVisibilityCanBePrivate", "unused")
    val energy: Double
        get() = 0.5 * inductance * current * current

    /**
     * Current across the device (as a whole), in Amps. See [i] for the current sourced by the Norton system.
     */
    val current: Double
        get() = if (eqR > 0) {
                    potential / eqR + i
                } else 0.0

    /**
     * The current amount of magnetic flux, in Webers (Volt * second), based on the instantaneous derivative of the current in Amperes.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    var phi: Double = 0.0

    override fun detail(): String {
        return "[inductor $name: ${potential}v, ${current}A, ${inductance}H, ${energy}J]"
    }

    override fun preStep(dt: Double) {
        if(ts != dt) ts = dt  // May cause a matrix solve--avoid this if possible
        i = phi / inductance
    }

    override fun postStep(dt: Double) {
        phi += potential * ts
    }

    override fun stamp() { 
        pos!!.stampResistor(neg!!, eqR)
    }

    protected fun unstamp() {
        if(pos != null && neg != null) pos!!.stampResistor(neg!!, -eqR)
    }
}
