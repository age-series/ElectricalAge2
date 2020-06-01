package org.eln2.sim

/**
 * An interface that represents a process.
 */
interface IProcess {
    /**
     * Runs the simulation for a specified time delta.
     * @param dt The duration of time to simulate.
     */
    fun process(dt: Double)
}
