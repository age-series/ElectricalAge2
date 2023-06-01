package org.eln2.mc.sim

import org.eln2.mc.Eln2
import org.eln2.mc.mathematics.*
import org.eln2.mc.utility.CsvLoader
import org.eln2.mc.utility.CsvNumeric
import org.eln2.mc.utility.ResourceReader

object Datasets {
    val AIR_THERMAL_CONDUCTIVITY = loadCsvSpline("air_thermal_conductivity/ds.csv", 0, 2)
    val MINECRAFT_TEMPERATURE_CELSIUS = loadCsvSpline("minecraft_temperature/ds.csv", 0, 1)
    val LEAD_ACID_12V_WET = loadCsvGrid2("lead_acid_12v/ds_wet.csv")

    private fun getCsv(name: String): CsvNumeric {
        return CsvLoader.loadNumericData(ResourceReader.getResourceString(Eln2.resource("datasets/$name")))
    }

    private fun loadCsvSpline(name: String, keyIndex: Int, valueIndex: Int): Spline {
        val builder = SplineBuilder()

        getCsv(name).also { csv ->
            csv.entries.forEach {
                builder.with(it[keyIndex], it[valueIndex])
            }
        }

        return builder.buildCubicKB()
    }

    private fun loadCsvGrid2(name: String): MappedGridInterpolator {
        val csv = getCsv(name)

        var xSize = 0
        var ySize = 0

        val xMapping = SplineBuilder().apply {
            csv.headers.drop(1).forEach { header ->
                with(header.toDouble(), (xSize++).toDouble())
            }
        }.buildCubicKB()

        val yMapping =SplineBuilder().apply {
            csv.entries.forEach {
                with(it[0], (ySize++).toDouble())
            }
        }.buildCubicKB()

        val grid = arrayKDGridDOf(xSize, ySize)

        for (y in 0 until ySize) {
            val row = csv.entries[y]

            row.values.drop(1).forEachIndexed { x, d ->
                grid[x, y] = d
            }
        }

        return MappedGridInterpolator(grid.interpolator(), listOf(xMapping, yMapping))
    }
}
