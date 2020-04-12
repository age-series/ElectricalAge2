package org.eln2.sim.electrical.mna.component

open class Resistor : Port() {
	override var name: String = "r"

	open var r: Double = 1.0
	open val i: Double
		get() = u / r
	open val p: Double
		get() = i * u

	override fun detail(): String {
		return "[resistor r:$r]"
	}

	override fun stamp() {
		if (!isInCircuit) return
		node(0).stampResistor(node(1), r)
	}
}

open class DynamicResistor : Resistor() {
	override var name = "rs"

	override var r: Double
		get() = super.r
		set(value) {
			// Remove our contribution to the matrix (using a negative resistance... should work)
			super.r = -super.r
			super.stamp()

			// Add our new contribution
			super.r = value
			super.stamp()
		}
}
