package org.eln2.oldsim.process.destruct

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.oldsim.thermal.ThermalLoad

open class ThermalLoadWatchDog(val thermalLoad: ThermalLoad): ValueWatchdog() {
    override fun getValue(): Double {
        return thermalLoad.getT()
    }

    open fun setThermalGrade(tg: ThermalGrade) {
        when(tg) {
            ThermalGrade.BASIC -> {
                max = 40.0
                min = 5.0
            }
            ThermalGrade.COMMERCIAL -> {
                max = 70.0
                min = 0.0
            }
            ThermalGrade.INDUSTRIAL -> {
                max = 85.0
                min = -40.0
            }
            ThermalGrade.MILITARY -> {
                max = 125.0
                min = -55.0
            }
            ThermalGrade.GODLIKE -> {
                max = 1000.0
                min = -273.15
            }
        }
    }

    open fun setCustomTemperatureRange(a: Double, b: Double) {
        if (a > b) {
            max = a
            min = b
        } else {
            max = b
            min = a
        }
    }
}

/*

Grade 	    Operating temperature
             Min 	        Max
Commercial  0°C / 32°F 	    70°C / 158°F
Industrial 	-40°C / -40°F 	85°C / 185°F
Military 	-55°C / -67°F 	125°C / 257°F

 */
enum class ThermalGrade {
    BASIC,
    COMMERCIAL,
    INDUSTRIAL,
    MILITARY,
    GODLIKE
}