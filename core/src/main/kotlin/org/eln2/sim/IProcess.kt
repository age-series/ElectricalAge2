package org.eln2.sim

interface IProcess {
	/**
	 * Runs the electrical simulation.
	 * @param dt The duration of time to simulate.
	 */
	fun process(dt: Double)
}
