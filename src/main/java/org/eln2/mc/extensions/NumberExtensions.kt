package org.eln2.mc.extensions

object NumberExtensions {
    fun Double.formatted(decimals: Int = 2): String {
        return "%.${decimals}f".format(this)
    }

    fun Float.formatted(decimals: Int = 2): String{
        return this.toDouble().formatted(decimals)
    }

    fun Double.formattedPercentN(decimals: Int = 2): String {
        return "${(this * 100.0).formatted(decimals)}%"
    }

    fun Float.formattedPercentN(decimals: Int = 2): String {
        return this.toDouble().formattedPercentN(decimals)
    }
}
