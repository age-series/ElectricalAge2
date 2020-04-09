package org.eln2.sim.electrical.mna

import org.eln2.sim.electrical.mna.IDetail

class VSource(var circuit: Circuit): IDetail {
    var current: Double = 0.0
    var index: Int = -1  // Assigned by Circuit
    var name = "vsource"

    override fun detail(): String {
        return "[vsource current: $current]"
    }
    
    fun stamp(pos: Node, neg: Node, v: Double) {
        circuit.stampVoltageSource(pos.index, neg.index, index, v)
    }
}