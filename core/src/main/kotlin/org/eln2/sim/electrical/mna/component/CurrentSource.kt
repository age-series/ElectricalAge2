package org.eln2.sim.electrical.mna.component

class CurrentSource : Port() {
	override var name: String = "is"

	var i: Double = 0.0
		set(value) {
			if (isInCircuit)
				circuit!!.stampCurrentSource(pos.index, neg.index, value - field)
			field = value
		}

	override fun detail(): String {
		return "[current source i:$i]"
	}

	override fun stamp() {
		if (!isInCircuit) return
		circuit!!.stampCurrentSource(pos.index, neg.index, i)
	}
}

