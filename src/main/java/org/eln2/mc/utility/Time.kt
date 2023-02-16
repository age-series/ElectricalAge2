package org.eln2.mc.utility

object Time {
    fun toSeconds(nano: Long): Double {
        return nano.toDouble() / 1000000000.0
    }
}
