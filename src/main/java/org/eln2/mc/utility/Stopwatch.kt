package org.eln2.mc.utility

import org.eln2.mc.data.Duration
import org.eln2.mc.data.TimeUnits

class Stopwatch {
    private var initialTimeStamp = System.nanoTime()
    private var lastTimeStamp = initialTimeStamp

    fun sample(): Duration {
        val current = System.nanoTime()
        val elapsedNanoseconds = current - lastTimeStamp
        lastTimeStamp = current

        return Duration.from(elapsedNanoseconds.toDouble(), TimeUnits.NANOSECOND)
    }

    val total get() = Duration.from((System.nanoTime() - initialTimeStamp).toDouble(), TimeUnits.NANOSECOND)

    fun resetTotal() {
        initialTimeStamp = System.nanoTime()
    }
}
