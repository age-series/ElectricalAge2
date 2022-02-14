package org.eln2.mc.utility

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ln

object SuffixConverter {
    fun convert(value : Long, decimalPlaces : Int, suffixes: Array<String>, unitSize : Int) : String
    {
        if (decimalPlaces < 0) throw Exception("decimal places must be larger than -1")
        if (value < 0) return "-" + convert(-value, decimalPlaces, suffixes, unitSize);
        if (value == 0L) { return "0.${"0".repeat(decimalPlaces)} ${suffixes[0]}" }

        var magnitude = (ln(value.toDouble()) / ln(unitSize.toDouble())).toInt()

        var adjustedSize = (value / (1L shl (magnitude * 10))).toBigDecimal();

        if (adjustedSize.setScale(decimalPlaces).toLong() >= 1000) {
            magnitude += 1;
            adjustedSize /= BigDecimal(1024);
        }

        return "${adjustedSize.setScale(decimalPlaces)} ${suffixes[magnitude]}"
    }
}
