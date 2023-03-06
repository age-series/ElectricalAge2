package org.eln2.mc.sim

import org.ageseries.libage.sim.thermal.STANDARD_TEMPERATURE
import org.ageseries.libage.sim.thermal.Simulator
import org.ageseries.libage.sim.thermal.Temperature

class TestEnvironment<Locator>: Simulator.Environment<Locator>{
    override fun conductance(locator: Locator): Double {
        return 0.0
    }

    override fun temperature(locator: Locator): Temperature {
        return STANDARD_TEMPERATURE
    }
}
