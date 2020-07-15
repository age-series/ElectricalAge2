package org.eln2.sim.electrical.mna.component

import org.eln2.debug.dprintln

/**
 * A capacitor.
 *   In order to not fill the source with theory, only relevant information will be given.
 *      See https://en.wikipedia.org/wiki/Capacitor for other details on capacitors if needed.
 *
 *   We referenced the Falstad circuit simulator (http://www.falstad.com/circuit/circuitjs.html), as our simulations
 *   are ultimately very similar. The relevant source can be found at
 *      https://github.com/hausen/circuit-simulator/blob/master/src/CapacitorElm.java
 *
 *   Capacitors are an electrical component that stores electric charge within a magnetic field. From a mathematical
 *      perspective, it relates electric charge to electrical potential. This is expressed by the ratio Q/V, in which
 *      the proportionality constant here is the capacitance C. As a circuit with current will change the amount of
 *      charge in the capacitor, the voltage will change proportionaly. That is, dQ/dV = C. When performing MNA, each
 *      component must report if it has any resistance, is sourcing any current or is sourcing any voltage. On the
 *      surface, these appear to not describe a capacitor at all; however, Norton's Theorum allows us to express the
 *      capacitor in terms of a current source and a resistor for a certain time interval.
 *
 *   First, the resistance of the equivalent resistor needs to be found. Ohm's Law gives the relation R = V/I.
 *      Substituting the capacitance relation (rearranged to dV = dQ/C) in results in the new equation R = Q/CI. Note
 *      that current the accumulation of charge over time (I = Q/t = dQ/dt). With this, the equation reduces down to
 *      R = dt/C. In reality, the "dt" term should be arbitrarily small, but we have the constraint that our sim needs
 *      to be realtime as well as having a considerably large time step. This means the dt/C will be an approximation
 *      of the equivalent resistance during the time step. As Falstad uses the same approximation, we opted to use it
 *      as well.
 *
 *   This leaves finding how much current should be sourced by the capacitor. This can be approached in a number of
 *      ways. Falstad uses Ohm's Law again (I = V/R) and uses the potential across the capacitor before the step to
 *      determine the current to source. This works, but can lead to the capacitor loosing all of its charge should the
 *      potential of the nodes be tampered with, such as connecting and disconnecting the capacitor to ground between
 *      sim steps. This takes a different approach by calculating how much charge is accumulated after each step and
 *      then employing the I = Q/t relation before the next step.
 *
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
    var timeStep: Double = 0.05  // A safe default
        set(value) {
            if(isInCircuit) unstamp()
            field = value
            if(isInCircuit) stamp()
        }

    /**
     * The "equivalent resistance" of the Norton system, in Ohms.
     */
    private val equivalentResistance: Double
        get() = timeStep / capacitance

    /**
     * Current across the device (as a whole), in Amps. See [internalCurrent] for the current sourced by the Norton system.
     */
    val current: Double
        get() = if (equivalentResistance > 0) {
            potential / equivalentResistance + internalCurrent
        } else 0.0

    /**
     * The current, in Amperes, presently sourced by this Norton system.
     */
    internal var internalCurrent: Double = 0.0
        set(value) {
            if (isInCircuit && pos != null && neg != null)
                circuit!!.stampCurrentSource(pos!!.index, neg!!.index, value - field)
            field = value
        }

    /**
     * Remember the potential across the capacitor from the previous simulation step.
     */
    private var lastPotential: Double = 0.0

    /**
     * The amount of charge this capacitor has current accumulated in coulumbs.
     */
    var charge: Double = 0.0

    /**
     * Returns a string containing all the relevant electrical information about this capacitor.
     */
    override fun detail(): String {
        return "[capacitor $name: ${potential}v, ${current}A (${internalCurrent}A), ${capacitance}F, ${energy}J, ${charge}C]"
    }

    /**
     * Function called by simulation before performing a step. Configures the virtual current source based on the charge
     * of the capacitor at this point in time.
     */
    override fun preStep(dt: Double) {
        if (timeStep != dt) timeStep = dt  // May cause conductivity change/matrix solve step--avoid this if possible
        internalCurrent = - charge / dt
        dprintln("v=$potential c=$charge is=$internalCurrent it=$current eqR=$equivalentResistance")
    }

    /**
     * Function called by simulation after performing a step. Performs accumulation of charge and updates memory state.
     */
    override fun postStep(dt: Double) {
        val dv = potential - lastPotential
        val dq = capacitance * dv

        charge += dq
        lastPotential = potential
        dprintln("dq = $dq, dv = $dv")
        dprintln("v=$potential c=$charge is=$internalCurrent it=$current eqR=$equivalentResistance")
    }

    /**
     * Commits the capacitor's equivalent resistance to the MNA matrix.
     */
    override fun stamp() {
        if (pos != null && neg != null) pos!!.stampResistor(neg!!, equivalentResistance)
    }

    /**
     * Removes the capacitor's equivalent resistance from the MNA matrix.
     */
    protected fun unstamp() {
        if (pos != null && neg != null) pos!!.stampResistor(neg!!, -equivalentResistance)
    }
}
