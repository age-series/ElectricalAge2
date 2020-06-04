package org.eln2.sim.electrical.mna.component

import org.eln2.sim.electrical.mna.Circuit

/**
 * A current source.
 *
 * This simply modifies the [Circuit.knowns] vector of two nodes to represent two independent currents--one in, and one out, thus satisfying the [Port] condition. Its current is controlled via [current].
 */
class CurrentSource : Port() {
    override var name: String = "is"

    /**
     * The current presently produced by this source, in Amperes.
     */
    var current: Double = 0.0
        set(value) {
            if (isInCircuit && pos != null && neg != null)
                circuit!!.stampCurrentSource(pos!!.index, neg!!.index, value - field)
            field = value
        }

    override fun detail(): String {
        return "[current source ${current}A, ${potential}V, ${potential * current}W]"
    }

    override fun stamp() {
        circuit!!.stampCurrentSource(pos!!.index, neg!!.index, current)
    }
}
