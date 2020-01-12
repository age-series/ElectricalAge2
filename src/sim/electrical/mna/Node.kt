package org.eln2.sim.electrical.mna

class Node: IDetail {
    var value: Double = 0.0
    var name = "node"

    constructor()
    constructor(p: Pair<Int, Int>) {
        name = "node${p.first}.${p.second}"
    }
    constructor(n: String) {
        name = n
    }

    override fun detail(): String {
        return "[node val: $value name: $name]"
    }
}