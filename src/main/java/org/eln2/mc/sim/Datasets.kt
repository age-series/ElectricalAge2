package org.eln2.mc.sim

import org.eln2.mc.Eln2
import org.eln2.mc.mathematics.*
import org.eln2.mc.data.CsvLoader
import org.eln2.mc.data.CsvNumeric
import org.eln2.mc.getResourceString

object Datasets {
    val AIR_THERMAL_CONDUCTIVITY = loadCsvSpline("air_thermal_conductivity/ds.csv", 0, 2)
    val MINECRAFT_TEMPERATURE_CELSIUS = loadCsvSpline("minecraft_temperature/ds.csv", 0, 1)
    val LEAD_ACID_12V_WET = loadCsvGrid2("lead_acid_12v/ds_wet.csv")

    fun loadKVP(name: String): List<Pair<String, String>> = readDataset(name).lines().filter { it.isNotBlank() }.map { line ->
        line.split("\\s".toRegex()).toTypedArray().let {
            require(it.size == 2) {
                "KVP \"$line\" mapped to ${it.joinToString(" ")}"
            }

            Pair(it[0], it[1])
        }
    }

    fun loadKVPSplineKB(name: String, mergeDuplicates: Boolean = true, t: Double = 0.0, b: Double = 0.0, c: Double = 0.0) = InterpolatorBuilder().let { sb ->
        loadKVP(name).map { (kStr, vStr) ->
            Pair(
                kStr.toDoubleOrNull() ?: error("Failed to parse key \"$kStr\""),
                vStr.toDoubleOrNull() ?: error("Failed to parse value \"$vStr\"")
            )
        }.let {
            if(mergeDuplicates) {
                val buckets = ArrayList<Pair<Double, ArrayList<Double>>>()

                it.forEach { (k, v) ->
                    fun create() = buckets.add(Pair(k, arrayListOf(v)))

                    if(buckets.isEmpty()) {
                        create()
                    }
                    else {
                        val (lastKey, lastBucket) = buckets.last()

                        if(lastKey == k) lastBucket.add(v)
                        else create()
                    }
                }

                val results = ArrayList<Pair<Double, Double>>()

                buckets.forEach { (key, values) ->
                    results.add(
                        Pair(
                            key,
                            values.sum() / values.size
                        )
                    )
                }

                results
            }
            else {
                it
            }
        }.forEach { (k, v) -> sb.with(k, v) }

        sb.buildCubic(t, b, c)
    }

    fun readDataset(name: String) = getResourceString(Eln2.resource("datasets/$name"))

    fun getCsv(name: String): CsvNumeric {
        return CsvLoader.loadNumericData(readDataset(name))
    }

    fun loadCsvSpline(name: String, keyIndex: Int, valueIndex: Int): Spline1d {
        val builder = InterpolatorBuilder()

        getCsv(name).also { csv ->
            csv.entries.forEach {
                builder.with(it[keyIndex], it[valueIndex])
            }
        }

        return builder.buildCubic()
    }

    fun loadCsvGrid2(name: String): MappedGridInterpolator {
        val csv = getCsv(name)

        var xSize = 0
        var ySize = 0

        val xMapping = InterpolatorBuilder().apply {
            csv.headers.drop(1).forEach { header ->
                with(header.toDouble(), (xSize++).toDouble())
            }
        }.buildCubic()

        val yMapping =InterpolatorBuilder().apply {
            csv.entries.forEach {
                with(it[0], (ySize++).toDouble())
            }
        }.buildCubic()

        val grid = arrayKDGridDOf(xSize, ySize)

        for (y in 0 until ySize) {
            val row = csv.entries[y]

            row.values.drop(1).forEachIndexed { x, d ->
                grid[x, y] = d
            }
        }

        return MappedGridInterpolator(grid.interpolator(), listOf(xMapping, yMapping))
    }

    fun init() {

    }
}
