package org.eln2.sim.electrical.mna.component

import org.eln2.sim.electrical.mna.Circuit
import org.eln2.sim.electrical.mna.Node

class VoltageSource: Port() {
    override var name: String = "vs"
    override val vsCount = 1

    override var u: Double = 0.0
        set(value) {
            if(isInCircuit)
                circuit!!.stampVoltageChange(vsources[0].index, value - field)
            field = value
        }
    val i: Double
        get() = if(isInCircuit) vsources[0].current else 0.0

    override fun detail(): String {
        return "[voltage source u:$u]"
    }

    override fun stamp() {
        if(!isInCircuit) return
        vsources[0].stamp(pos, neg, u)
    }
}