package org.eln2.sim.electrical.mna.component

/**
 * An inductor.
 *
 * Inductors are defined by a characteristic of "self-inductance", wherein magnetic flux (change in magnetic field) induces an electric field, quantified as magnetic flux per unit of current. Since induced voltage is proportional to magnetic flux, we can take the derivative of current with respect to time and determine instantaneous voltage.
 *
 * Of the two out-of-phase values that could be stored (the derivative of current, or the magnetic flux), this implementation uses the magnetic flux.
 *
 * This reactive component is simulated using a Norton system, as designed by Falstad. It is not "non-linear", however, and does not need any substeps to compute. It does, however, need to know the timescale of the simulation steps.
 */
open class Inductor : Port() {
	override var name: String = "l"

	/**
	 * Self-inductance in Henries, singular Henry (Volts / Ampere).
	 */
	var l: Double = 0.0
	/**
	 * The simulation timestep in seconds.
	 *
	 * This is set in [preStep], but the value is unfortunately not available during [stamp]; thus, it may be slightly out of date when [step] is actually called.
	 */
	var ts: Double = 0.05  // A safe default
	/**
	 * The "equivalent resistance" of the Norton system, in Ohms.
	 */
	val eqR: Double
		get() = l / ts
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
	 * The current amount of magnetic flux, in Webers (Volt * second), based on the instantaneous derivative of the current in Amperes.
	 */
	var phi: Double = 0.0

	override fun detail(): String {
		return "[inductor h:$l]"
	}

	override fun preStep(dt: Double) {
		ts = dt
		i = phi / l
	}

	override fun postStep(dt: Double) {
		phi += potential * ts
	}

	override fun stamp() {
		node(0).stampResistor(node(1), eqR)
	}
}
