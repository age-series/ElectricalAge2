package org.eln2.sim.thermal

/**
 * @param outerSurfaceArea The surface area (m^2) of anything that is not a surface that conducts with a Thermal Node
 * @param thermalNodes Map of Thermal Nodes's and the surface area we have at them (m^2)
 */
class ThermalEdge(
    val thermalNodes: Pair<ThermalNode, ThermalNode>,
    val conductionArea: Double,
    val conductionDistance: Double
) {
    internal var hasBeenCalculatedYet: Boolean = false
    fun clearRunBit() {
        hasBeenCalculatedYet = false
    }

    fun getThermalGradient() = thermalNodes.second.temperature - thermalNodes.first.temperature

/*

                  Tn4
                  |
o ---- Tn1 -Te1- Tn2 -Te2- Tn3 ------------------------- o
                 |
                Tn5

 */

    @Synchronized
    fun queueMoveEnergy(dt: Double) {
        val n1 = thermalNodes.first
        val n2 = thermalNodes.second

        val x1 = conductionDistance / n1.material.thermalConductivity / conductionArea
        val x2 = conductionDistance / n2.material.thermalConductivity / conductionArea
        val x3 = 1.0 / 0.0005 / conductionArea

        val heatFlow = getThermalGradient() / (x1 + x2 + x3)

        n1.energyChanges.add(heatFlow * dt)
        n2.energyChanges.add(heatFlow * dt)

        /*val t1 = conductionNodes.filter { it.key != tn }.entries.first().key
        val t2 = tn
        if (toElement.hasBeenCalculatedYet) return
        if (!conductionNodes.containsKey(tn)) throw Exception("Thermal Element does not have surface area in common with Thermal Transfer Point")
        if (!toElement.conductionNodes.containsKey(tn)) throw Exception("Thermal Element does not have surface area in common with Thermal Transfer Point")
        val surfaceAreaFrom  = conductionNodes[tn]!!
        val surfaceAreaTo = toElement.conductionNodes[tn]!!
        val commonSurfaceArea = min(surfaceAreaFrom, surfaceAreaTo)
        // calculation
        val heatFlow: Double = 0.0*/
    }
}

/*
https://en.wikipedia.org/wiki/Thermal_contact_conductance
q = (T2 - T1) / ( dx_a/(k_a*A) + 1/(h_c*A) + dx_b/(k_b*A) )
 */
