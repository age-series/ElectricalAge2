package org.eln2.sim.electrical.mna.component

import org.eln2.sim.electrical.mna.Circuit
import org.eln2.sim.electrical.mna.VSource

/**
 * A voltage source.
 *
 * This simply proposes one [VSource] to the [Circuit] in which it is contained. Its potential is controlled via [potential].
 */
class VoltageSource : Port() {
    override var name: String = "vs"
    override val imageName = "vsource"
    override val vsCount = 1

    /**
     * The current potential of this source, in Volts.
     */
    override var potential: Double = 0.0
        set(value) {
            if (isInCircuit)
                vsources[0].change(value - field)
            field = value
        }

    /**
     * The current through this source, as a result of the simulation step.
     */
    val current: Double
        get() = if (isInCircuit) vsources[0].current else 0.0

    override fun detail(): String {
        return "[voltage source ${potential}V, ${current}A, ${potential * current}W]"
    }

    override fun stamp() {
        if (!isInCircuit) return
        vsources[0].stamp(pos!!, neg!!, potential)
    }
}
