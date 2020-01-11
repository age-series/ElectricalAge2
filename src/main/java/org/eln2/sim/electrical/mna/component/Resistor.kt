package org.eln2.sim.electrical.mna.component

import org.eln2.sim.electrical.mna.Circuit
import org.eln2.sim.electrical.mna.Node

open class Resistor: Component() {
    override var name: String = "r"
    override var nodes: MutableList<Node?> = mutableListOf()

    var r: Double = 0.0

    override fun detail(): String {
        return "[resistor r:$r]"
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