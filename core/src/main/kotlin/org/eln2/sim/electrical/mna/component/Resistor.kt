package org.eln2.sim.electrical.mna.component

class Resistor(r: Double) : Port() {
    override val name: String = "r"

    override var resistance: Double = r

    override fun stamp() {
        circuit!!.stampResistor(pos.index, neg.index, resistance)
    }

    override fun detail(): String {
        return "$name: V = $potential, I = $current, R = $resistance"
    }
}
