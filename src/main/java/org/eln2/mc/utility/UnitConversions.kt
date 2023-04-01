package org.eln2.mc.utility

object UnitConversions {
    /**
     * Converts watt-hours to joules.
     * */
    fun wattHoursInJ(wattHours: Double): Double{
        return wattHours * 3.6e+3
    }

    /**
     * Converts kilowatt-hours to joules.
     * */
    fun kwHoursInJ(kiloWattHours: Double): Double{
        return kiloWattHours * 3.6e+6
    }
}
