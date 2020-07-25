package org.eln2.sim.thermal

class ThermalNode(
    var temperature: Double = 20.0
) {
    var thermalElements = mutableSetOf<ThermalEdge>()
    var thermalChanges = mutableSetOf<Double>()
}
