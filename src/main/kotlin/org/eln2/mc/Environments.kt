package org.eln2.mc

import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import org.ageseries.libage.sim.thermal.Temperature
import org.ageseries.libage.sim.thermal.ThermalUnits
import org.eln2.mc.data.BlockLocator
import org.eln2.mc.data.DataFieldMap
import org.eln2.mc.data.LocatorSet
import org.eln2.mc.data.requireLocator
import java.util.concurrent.ConcurrentHashMap

fun interface EnvTemperatureField {
    fun readTemperature(): Temperature
}

fun interface EnvThermalConductivityField {
    fun readConductivity(): Double
}

data class EnvironmentInformation(
    val temperature: Temperature,
    val airThermalConductivity: Double,
) {
    fun fieldMap() = DataFieldMap()
        .withField { EnvTemperatureField { temperature } }
        .withField { EnvThermalConductivityField { airThermalConductivity } }
}

object BiomeEnvironments {
    private val biomes = ConcurrentHashMap<Biome, EnvironmentInformation>()

    private val AIR_THERMAL_CONDUCTIVITY = loadCsvSpline("air_thermal_conductivity/ds.csv", 0, 2)
    private val MINECRAFT_TEMPERATURE_CELSIUS = loadCsvSpline("minecraft_temperature/ds.csv", 0, 1)

    fun cellEnv(level: Level, pos: LocatorSet): EnvironmentInformation {
        val biome = level.getBiome(pos.requireLocator<BlockLocator> {
            "Biome Environments need a block pos locator"
        }).value()

        val temperature = MINECRAFT_TEMPERATURE_CELSIUS.evaluate(biome.baseTemperature.toDouble())

        return biomes.computeIfAbsent(biome) {
            return@computeIfAbsent EnvironmentInformation(
                Temperature.from(temperature, ThermalUnits.CELSIUS),
                AIR_THERMAL_CONDUCTIVITY.evaluate(temperature)
            )
        }
    }
}
