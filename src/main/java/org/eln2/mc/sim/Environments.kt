package org.eln2.mc.sim

import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import org.ageseries.libage.sim.thermal.STANDARD_TEMPERATURE
import org.ageseries.libage.sim.thermal.Simulator
import org.ageseries.libage.sim.thermal.Temperature
import org.ageseries.libage.sim.thermal.ThermalUnits
import org.eln2.mc.common.cells.foundation.CellPos
import java.util.concurrent.ConcurrentHashMap

class TestEnvironment<Locator>: Simulator.Environment<Locator>{
    override fun conductance(locator: Locator): Double {
        return 0.0
    }

    override fun temperature(locator: Locator): Temperature {
        return STANDARD_TEMPERATURE
    }
}

object BiomeEnvironments {
    private val biomes = ConcurrentHashMap<Biome, Simulator.Environment<CellPos>>()

    fun get(level: Level, pos: CellPos): Simulator.Environment<CellPos>{
        val biome = level.getBiome(pos.blockPos).value()

        return biomes.computeIfAbsent(biome) {
            return@computeIfAbsent object: Simulator.Environment<CellPos> {
                override fun conductance(locator: CellPos): Double {
                    return Datasets.AIR_THERMAL_CONDUCTIVITY.evaluate(biome.baseTemperature.toDouble())
                }

                override fun temperature(locator: CellPos): Temperature {
                    return Temperature.from(biome.baseTemperature.toDouble(), ThermalUnits.CELSIUS)
                }
            }
        }
    }
}
