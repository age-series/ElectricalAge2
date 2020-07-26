package org.eln2.sim.thermal

import org.eln2.sim.Material

class ThermalNode(
    var material: Material,
    var volume: Double,
    var temperature: Double = 20.0,
    // TODO: This is for having different surface areas depending on direction of connectivity.
    // TODO: Not sure how we want to do that or if its already been implemented, but this is my
    // TODO: guess on what it will look along the lines of.
    var surfaceArea: MutableMap<Int, Double>
) {
    var thermalEdges = mutableSetOf<ThermalEdge>()
    var energyChanges = mutableSetOf<Double>()

    val mass = volume * material.density

    var energy
        get() = mass * material.specficHeat * temperature
        set(value) { temperature = energy / mass / material.specficHeat }

    @Synchronized
    fun commitThermalChanges() {
        energyChanges.forEach { energy += it }
    }
}
