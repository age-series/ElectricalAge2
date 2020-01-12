package org.eln2.sim.electrical.mna.component

import org.eln2.sim.electrical.mna.Circuit
import org.eln2.sim.electrical.mna.Node

class VoltageSource: Component() {
    override var name: String = "vs"
    override var nodes: MutableList<Node?> = mutableListOf()

    var u: Double = 0.0

    override fun detail(): String {
        return "[voltage source u:$u]"
    }

    override fun stampMatrix(c: Circuit) {
        //doot
    }

    override fun stampRight(c: Circuit) {
        //doot
    }

    override fun update(c: Circuit) {
        // this calls stamp when needed.
    }
}