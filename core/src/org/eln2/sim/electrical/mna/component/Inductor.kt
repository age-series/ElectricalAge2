package org.eln2.sim.electrical.mna.component

import org.eln2.sim.electrical.mna.Circuit
import org.eln2.sim.electrical.mna.Node

open class Inductor: Port() {
    override var name: String = "l"

    var h: Double = 0.0
    var ts: Double = 0.05  // A safe default
    val eqR: Double
        get() = h / ts
    internal var i: Double = 0.0
        set(value) {
            if(isInCircuit)
                circuit!!.stampCurrentSource(pos.index, neg.index, value - field)
            field = value
        }
    var lastI: Double = 0.0

    override fun detail(): String {
        return "[inductor h:$h]"
    }

    override fun preStep(dt: Double) {
        ts = dt
    }

    override fun postStep(dt: Double) {
        i += u / eqR
    }

    override fun stamp() {
        node(0).stampResistor(node(1), eqR)
    }
}