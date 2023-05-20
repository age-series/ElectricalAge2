package org.eln2.mc.extensions

fun Double.formatted(decimals: Int = 2): String {
    return "%.${decimals}f".format(this)
}

fun Double.formattedPercentN(decimals: Int = 2): String {
    return "${(this * 100.0).formatted(decimals)}%"
}
