package org.eln2.mc

import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import org.ageseries.libage.sim.thermal.Temperature
import org.ageseries.libage.sim.thermal.ThermalUnits
import org.eln2.mc.data.BlockLocator
import org.eln2.mc.data.HashDataTable
import org.eln2.mc.data.Locator
import org.eln2.mc.data.requireLocator
import java.util.concurrent.ConcurrentHashMap

fun interface EnvironmentalTemperatureField {
    fun readTemperature(): Temperature
}

fun EnvironmentalTemperatureField.readInto(body: ThermalBody) {
    body.temperature = this.readTemperature()
}

fun interface EnvironmentalThermalConductivityField {
    fun readConductivity(): Double
}

data class EnvironmentInformation(
    val temperature: Temperature,
    val airThermalConductivity: Double,
) {
    fun fieldMap() = HashDataTable()
        .withField { EnvironmentalTemperatureField { temperature } }
        .withField { EnvironmentalThermalConductivityField { airThermalConductivity } }
}

object BiomeEnvironments {
    private val biomes = ConcurrentHashMap<Biome, EnvironmentInformation>()

    private val AIR_THERMAL_CONDUCTIVITY = loadCsvSpline("air_thermal_conductivity/ds.csv", 0, 2)
    private val MINECRAFT_TEMPERATURE_CELSIUS = loadCsvSpline("minecraft_temperature/ds.csv", 0, 1)

    fun getInformationForBlock(level: Level, pos: Locator): EnvironmentInformation {
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
