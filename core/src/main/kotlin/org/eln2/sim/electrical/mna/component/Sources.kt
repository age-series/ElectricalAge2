package org.eln2.sim.electrical.mna.component

import org.eln2.sim.electrical.mna.VSource

/**
 * An ideal two-pin voltage source. It is simply a container for [VSource].
 */
class VoltageSource : Port() {
    override var name : String = "vs"
    override val imageName = "vsource"
    override val vsCount = 1

    override var potential: Double
        get() = vsources.first().potential
        set(value) { vsources.first().potential = value }

    // The resistance over an ideal voltage source is zero.
    override val resistance: Double = 0.0

    override var current: Double
        get() = vsources.first().current
        set(value) { vsources.first().current = value }

    override fun stamp() {
        if (isInCircuit) vsources.first().stamp(pos, neg, potential)
    }

    override fun detail(): String {
        return "$name: V+ = ${pos.potential}, V- = ${neg.potential}, Vs = $potential, I = $current"
    }
}

/**
 * An ideal two-pin current source.
 */
class CurrentSource : Port() {
    override val name: String = "cs"

    override val resistance: Double = 0.0

    /**
     * The current in Amperes that this current source produces.
     */
    override var current: Double = 0.0

    override fun stamp() {
        circuit!!.stampCurrentSource(pos.index, neg.index, current)
    }

    override fun detail(): String {
       return "$name: V = $potential, I = $current"
    }
}
