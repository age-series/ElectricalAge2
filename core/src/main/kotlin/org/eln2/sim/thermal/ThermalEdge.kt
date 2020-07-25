package org.eln2.sim.thermal

import org.eln2.sim.Material
import kotlin.math.min

/**
 * Thermal Edge represents a physical object with a given temperature and thermal capacity.
 *
 * @param material The material of the Thermal Edge
 * @param volume The volume of the material (m^3)
 * @param outerSurfaceArea The surface area (m^2) of anything that is not a surface that conducts with a Thermal Node
 * @param conductionNodes Map of Thermal Nodes's and the surface area we have at them (m^2)
 */
class ThermalEdge(
    val material: Material,
    val volume: Double,
    val outerSurfaceArea: Double,
    val conductionNodes: MutableMap<ThermalNode, Double>
) {
    internal var hasBeenCalculatedYet: Boolean = false
    fun clearRunBit() {
        hasBeenCalculatedYet = false
    }

    fun computeTransfer(to: ThermalNode, dt: Double) {
        if (!to.thermalElements.contains(this)) {
            throw Exception("Thermal Element must be contained in thermal transfer point")
        }
        to.thermalElements.forEach {
            if (!it.conductionNodes.contains(to)) throw Exception("Thermal Element must be contained in thermal transfer point")
            queueMoveEnergy(it, to)
        }
    }

/*

                  Tn4
                  |
o ---- Tn1 -Te1- Tn2 -Te2- Tn3 ------------------------- o
                 |
                Tn5

 */

    @Synchronized
    private fun queueMoveEnergy(toElement: ThermalEdge, tn: ThermalNode) {
        if (toElement.hasBeenCalculatedYet) return
        if (!conductionNodes.containsKey(tn)) throw Exception("Thermal Element does not have surface area in common with Thermal Transfer Point")
        if (!toElement.conductionNodes.containsKey(tn)) throw Exception("Thermal Element does not have surface area in common with Thermal Transfer Point")
        val surfaceAreaFrom  = conductionNodes[tn]!!
        val surfaceAreaTo = toElement.conductionNodes[tn]!!
        val commonSurfaceArea = min(surfaceAreaFrom, surfaceAreaTo)
        val t1 = conductionNodes.filter { it.key != tn }.entries.first().key
        val t2 = tn
        val t3 = toElement.conductionNodes.filter { it.key != tn }.entries.first().key
        // calculation
        val heatFlow: Double = 0.0
    }
}
