package org.eln2.mc.utility

class Stopwatch {
    private var initialTimeStamp = System.nanoTime()
    private var lastTimeStamp = initialTimeStamp

    fun sample(): Double {
        val current = System.nanoTime()
        val elapsedNanoseconds = current - lastTimeStamp
        lastTimeStamp = current

        return Time.toSeconds(elapsedNanoseconds)
    }

    val total get() = Time.toSeconds(System.nanoTime() - initialTimeStamp)

    fun resetTotal() {
        initialTimeStamp = System.nanoTime()
    }
}
