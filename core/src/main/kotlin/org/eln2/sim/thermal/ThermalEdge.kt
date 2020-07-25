package org.eln2.sim.thermal

import org.eln2.sim.Material
import java.lang.Exception
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
    val outerSurfaceArea: Double,
    val conductionNodes: Pair<ThermalNode, ThermalNode>,
    val conductionArea: Double,
    val conductionDistance: Double
) {
    internal var hasBeenCalculatedYet: Boolean = false
    fun clearRunBit() {
        hasBeenCalculatedYet = false
    }

/*

                  Tn4
                  |
o ---- Tn1 -Te1- Tn2 -Te2- Tn3 ------------------------- o
                 |
                Tn5

 */

    @Synchronized
    fun queueMoveEnergy() {
        // Assert that the edge is connected to two nodes. Having an edge with only one side connected or no sides
        // connected at all makes no sense and would definitely be a bug.
        val n1 = conductionNodes.first ?: throw Exception("Dangling thermal edge detected!")
        val n2 = conductionNodes.second ?: throw Exception("Dangling thermal edge detected!")

        //val thermalGradient = t2 - t1;


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
