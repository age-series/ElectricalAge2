package org.eln2.sim.electrical.mna.component

import org.eln2.sim.electrical.mna.Circuit
import org.eln2.sim.electrical.mna.Node

open class Capacitor: Component() {
    override var name: String = "c"
    override val nodeCount = 2

    var c: Double = 0.0
    var ts: Double = 0.05  // A safe default
    val eqR: Double
        get() = ts / c
    internal var i: Double = 0.0
        set(value) {
            if(isInCircuit)
                circuit!!.stampCurrentSource(node(0).index, node(1).index, value - field)
            field = value
        }
    var lastI: Double = 0.0

    override fun detail(): String {
        return "[capacitor c:$c]"
    }

    override fun preStep(dt: Double) {
        ts = dt
        i = (node(1).potential - node(0).potential) / eqR
    }

    override fun postStep(dt: Double) {
        lastI = (node(0).potential - node(1).potential) / eqR + i
    }

    override fun stamp() {
        node(0).stampResistor(node(1), eqR)
    }
}