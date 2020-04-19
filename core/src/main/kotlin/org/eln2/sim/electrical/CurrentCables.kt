package org.eln2.sim.electrical

import org.eln2.sim.IProcess
import org.eln2.sim.electrical.mna.component.Resistor
import org.eln2.sim.materials.Material

class CurrentCables(length: Double = 1.0, conductorArea: Double = 1.0, insulatorThickness: Double = 1.0, material: Material = Material.COPPER): IProcess {

	/**
	 * Length of the cable in meters
	 */
	var length: Double = length

	/**
	 * Conductor Area in mm^2
	 */
	var conductorArea: Double = conductorArea

	/**
	 * Insulator thickness in mm
	 */
	var insulatorThickness: Double = insulatorThickness

	/**
	 * Material Type (copper, iron, steel, etc.)
	 */
	val conductorType: Material = material

	/**
	 * Resistance of the cable as a whole
	 */
	val resistance: Double
		get() {
			val r = conductorType.resistivity * ((conductorArea * 1e-6) / length) // resistivity (ohms/meter)* (cross sectional area (m) / length (m))
			resistor.resistance = r
			return r
		}

	private val resistor: Resistor = Resistor()

	override fun process(time: Double) {
		// This is the amount of energy lost to the cable's resistance in the amount of time that has passed.
		val joules = resistor.p / time
		// somehow, convert this number into an amount of temperature in the cable, as well as temperature that is being radiated out of the cable. Not sure how to sim that.
	}
}
