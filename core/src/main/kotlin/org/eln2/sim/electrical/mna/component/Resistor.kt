package org.eln2.sim.electrical.mna.component

open class Resistor : Port() {
	override var name: String = "r"
	override val imageName = "resistor"

	open var resistance: Double = 1.0
	open val current: Double
		get() = potential / resistance
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

open class DynamicResistor : Resistor() {
	override var name = "rs"

	override var resistance: Double
		get() = super.resistance
		set(value) {
			// Remove our contribution to the matrix (using a negative resistance... should work)
			super.resistance = -super.resistance
			super.stamp()

			// Add our new contribution
			super.resistance = value
			super.stamp()
		}
}
