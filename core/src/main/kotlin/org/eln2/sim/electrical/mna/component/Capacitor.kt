package org.eln2.sim.electrical.mna.component

class Capacitor(var capacitance: Double) : Port() {
    override val name: String = "c"

    override val resistance: Double = if (circuit != null) { circuit!!.timeStep / capacitance } else { 0.0 }

    val charge : Double = capacitance * potential

    private var internalCurrentSource = CurrentSource()

    override val current: Double
        get() = internalCurrentSource.current

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
        internalCurrentSource.current = -potential / resistance
        internalCurrentSource.stamp()
        if (resistance > 0.0) circuit!!.stampResistor(pos.index, neg.index, resistance)
    }

    override fun detail(): String {
        return "$name: V = $potential, I = $current, R = $resistance, C = $capacitance, Q = $charge"
    }
}
