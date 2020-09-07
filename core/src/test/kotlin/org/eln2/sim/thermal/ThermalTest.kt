package org.eln2.sim.thermal

import org.eln2.sim.Material
import org.junit.jupiter.api.Test

class ThermalTest {
    // TODO: Tests

    @Test
    fun basicConnectivity() {
        val ts = ThermalSimulator()

        var cubeSurfaceArea: MutableMap<Int, Double> = mutableMapOf()
        (0 until 6).forEach {
            cubeSurfaceArea[it] = 1.0
        }

        var tn1 = ThermalNode(Material.COPPER, 1.0, 20.0, cubeSurfaceArea)
        var tn2 = ThermalNode(Material.COPPER, 1.0, 40.0, cubeSurfaceArea)

        ts.connectNodes(tn1, tn2, 1, 1.0)

        (0 .. 100).forEach {
            ts.process(1.0)
            println("Temperature of T1: ${tn1.temperature}")
            println("Temperature of T2: ${tn2.temperature}")
        }
    }
}
