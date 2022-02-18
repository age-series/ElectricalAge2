package org.eln2.mc.utility

import java.math.BigDecimal
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow

object SuffixConverter {
    private val prefixes = mapOf(
        -24 to "y",
        -21 to "z",
        -18 to "a",
        -15 to "f",
        -12 to "p",
        -9 to "n",
        -6 to "μ",
        -3 to "m",
        0 to "",
        3 to "k",
        6 to "M",
        9 to "G",
        12 to "T",
        15 to "P",
        18 to "E",
        21 to "Z",
        24 to "Y"
    )

    fun convert(value: Double, unit: String, precision: Int): String {
        var mag = floor(log10(value)).toInt()

        while (mag % 3 != 0) {
            mag--
        }

        if (mag < -24) {
            mag = -24
        } else if (mag > 24) {
            mag = 24
        }

        val converted = value / 10.0.pow(mag.toDouble())
        return "%.${precision}f ${prefixes[mag]}$unit".format(converted)
    }

}
