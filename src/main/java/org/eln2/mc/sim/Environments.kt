package org.eln2.mc.sim

import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import org.ageseries.libage.sim.thermal.Temperature
import org.ageseries.libage.sim.thermal.ThermalUnits
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.data.BlockPosLocator
import org.eln2.mc.data.R3
import org.eln2.mc.data.requireLocator
import org.eln2.mc.data.DataFieldMap
import java.util.concurrent.ConcurrentHashMap

fun interface EnvTemperatureField {
    fun readTemperature(): Temperature
}

fun interface EnvThermalConductivityField {
    fun readConductivity(): Double
}

data class EnvironmentInformation(
    val temperature: Temperature,
    val airThermalConductivity: Double
) {
    fun fieldMap() = DataFieldMap()
        .withField { EnvTemperatureField { temperature } }
        .withField { EnvThermalConductivityField { airThermalConductivity } }
}

object BiomeEnvironments {
    private val biomes = ConcurrentHashMap<Biome, EnvironmentInformation>()

    fun cellEnv(level: Level, pos: CellPos): EnvironmentInformation {
        val biome = level.getBiome(pos.descriptor.requireLocator<R3, BlockPosLocator> {
            "Biome Environments need a block pos locator"
        }.pos).value()

        val temperature = Datasets.MINECRAFT_TEMPERATURE_CELSIUS.evaluate(biome.baseTemperature.toDouble())

        return biomes.computeIfAbsent(biome) {
            return@computeIfAbsent EnvironmentInformation(
                Temperature.from(temperature, ThermalUnits.CELSIUS),
                Datasets.AIR_THERMAL_CONDUCTIVITY.evaluate(temperature)
            )
        }
    }
}
