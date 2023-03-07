package org.eln2.mc.sim

import org.eln2.mc.Eln2
import org.eln2.mc.mathematics.HermiteSplineMapped
import org.eln2.mc.mathematics.mappedHermite
import org.eln2.mc.utility.CsvLoader
import org.eln2.mc.utility.ResourceReader

object Datasets {
    val AIR_THERMAL_CONDUCTIVITY = loadCsvSpline("air_thermal_conductivity/ds.csv", 0, 2)

    private fun loadCsvSpline(name: String, keyIndex: Int, valueIndex: Int): HermiteSplineMapped {
        val builder = mappedHermite()

        CsvLoader.loadNumericData(ResourceReader.getResourceString(Eln2.resource("datasets/$name"))).also { csv ->
            csv.entries.forEach {
                builder.point(it[keyIndex], it[valueIndex])
            }
        }

        return builder.buildHermite()
    }
}
