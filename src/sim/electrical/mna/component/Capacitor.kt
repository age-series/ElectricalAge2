package org.eln2.sim.electrical.mna.component

import org.eln2.sim.electrical.mna.Circuit
import org.eln2.sim.electrical.mna.Node

open class Capacitor: Component() {
    override var name: String = "c"
    override var nodes: MutableList<Node?> = mutableListOf()

    var c: Double = 0.0

    override fun detail(): String {
        return "[capacitor c:$c]"
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