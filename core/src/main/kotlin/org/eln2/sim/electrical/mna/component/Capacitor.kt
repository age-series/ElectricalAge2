package org.eln2.sim.electrical.mna.component

class Capacitor(var capacitance: Double) : Port() {
    override val name: String = "c"

    override val resistance: Double
      get() = if (circuit == null) { 0.0 } else { circuit!!.timeStep / capacitance }

    val charge : Double
        get() = capacitance * potential

    private var internalCurrentSource = CurrentSource()

    override val current: Double
        get() = if (resistance > 0.0) internalCurrentSource.current else 0.0

    override fun added() {
        internalCurrentSource.circuit = circuit
        internalCurrentSource.nodes.add(neg)
        internalCurrentSource.nodes.add(pos)
    }

    override fun removed() {
        internalCurrentSource.circuit = null
        internalCurrentSource.nodes.clear()
    }

    override fun stamp() {
        if (resistance > 0.0) {
            circuit!!.stampResistor(pos.index, neg.index, resistance)
            internalCurrentSource.current = -potential / resistance
            internalCurrentSource.stamp()
        }
    }

    override fun detail(): String {
        return "$name: V = $potential, I = $current, R = $resistance, C = $capacitance, Q = $charge"
    }
}
