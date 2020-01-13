package org.eln2.sim.electrical.mna.component

import org.eln2.sim.electrical.mna.Circuit
import org.eln2.sim.electrical.mna.Node

class CurrentSource: Component() {
    override var name: String = "is"
    override val nodeCount = 2

    var i: Double = 0.0
        set(value) {
            if(isInCircuit)
                circuit!!.stampCurrentSource(pos.index, neg.index, value - field)
            field = value
        }
    val pos: Node
        get() = node(0)
    val neg: Node
        get() = node(1)

    override fun detail(): String {
        return "[current source i:$i]"
    }

    override fun stamp() {
        if(!isInCircuit) return
        circuit!!.stampCurrentSource(pos.index, neg.index, i)
    }
}

