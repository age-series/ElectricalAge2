package org.eln2.mc.utility

/**
 * Utility class for converting multiples of units to the base unit.
 * The function names are indicative of the unit in question.
 * */
object SelfDescriptiveUnitMultipliers {
    fun milliOhms(value: Double): Double{
        return value * 1e-3
    }

    fun kiloJoules(value: Double): Double {
        return value * 1e3
    }

    fun megaJoules(value: Double): Double{
        return value * 1e6
    }

    fun centimeters(value: Double): Double {
        return value / 100.0
    }
}
