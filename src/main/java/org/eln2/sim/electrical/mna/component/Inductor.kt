package org.eln2.sim.electrical.mna.component

import org.eln2.sim.electrical.mna.Circuit
import org.eln2.sim.electrical.mna.Node

open class Inductor: Component() {
    override var name: String = "l"
    override var nodes: MutableList<Node?> = mutableListOf()

    var h: Double = 0.0

    override fun detail(): String {
        return "[inductor h:$h]"
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