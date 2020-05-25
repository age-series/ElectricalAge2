package org.eln2.sim.electrical.mna

import org.eln2.sim.IProcess
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class IProcessTest : IProcess{
	private var time_run : Double = 0.0
	@Test
	fun iProcessTest() {
		repeat(20) { process(0.05); } // Run the simulation 20 times for 50 ms each.
		Assertions.assertEquals(time_run, 1.0, 0.001) // Total time elapsed should be one second.
	}

	override fun process(dt: Double) {
		time_run += dt
	}
}
