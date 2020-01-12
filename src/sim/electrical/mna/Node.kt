package org.eln2.sim.electrical.mna

class Node: IDetail {
    var value: Double = 0.0
    var name = "node"

    override fun detail(): String {
        return "[node val: $value]"
    }
}