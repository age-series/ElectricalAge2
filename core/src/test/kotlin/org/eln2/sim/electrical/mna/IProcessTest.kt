package org.eln2.sim.electrical.mna

import org.eln2.sim.IProcess
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class IProcessTest : IProcess{
	private var time_run : Double = 0.0;
	@Test
	fun iProcessTest() {
		repeat(20) { process(0.05); } // Run the simulation 20 times with a duration of one MC tick.
		Assertions.assertEquals(time_run, 1.0, 0.001); // Total time elapsed should be one second.
	}

	override fun process(dt: Double) {
		// Pretend to do the simulation
		time_run += dt;
	}
}
