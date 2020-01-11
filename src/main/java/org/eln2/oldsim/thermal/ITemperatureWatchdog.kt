package org.eln2.oldsim.thermal

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

interface ITemperatureWatchdog {
    fun getUmax(): Double
    fun getUmin(): Double
    fun getBreakPropPerVoltOverflow(): Double
}