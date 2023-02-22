package org.eln2.mc.extensions

object NumberExtensions {
    fun Double.formatted(decimals: Int = 2): String {
        return "%.${decimals}f".format(this)
    }

    fun Float.formatted(decimals: Int = 2): String{
        return this.toDouble().formatted(decimals)
    }
}
