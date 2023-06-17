package org.eln2.mc.utility

import org.eln2.mc.data.*

class Stopwatch {
    private var initialTimeStamp = System.nanoTime()
    private var lastTimeStamp = initialTimeStamp

    fun sample(): Quantity<Time> {
        val current = System.nanoTime()
        val elapsedNanoseconds = current - lastTimeStamp
        lastTimeStamp = current

        return Quantity(elapsedNanoseconds.toDouble(), NANOSECONDS)
    }

    val total get() = Quantity<Time>((System.nanoTime() - initialTimeStamp).toDouble(), NANOSECONDS)

    fun resetTotal() {
        initialTimeStamp = System.nanoTime()
    }
}
