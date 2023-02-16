package org.eln2.mc.utility

object Chrono {
    fun toSeconds(nano : Long) : Double{
        return nano.toDouble() / 1000000000.0
    }
}
