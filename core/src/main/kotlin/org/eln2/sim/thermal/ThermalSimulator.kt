package org.eln2.sim.thermal
import org.eln2.sim.IProcess

/**
 * main thermal simulation class
 */
class ThermalSimulator: IProcess {
    var thermalEdges = mutableSetOf<ThermalEdge>()
    override fun process(dt: Double) {
        val threadList = mutableSetOf<Thread>()
        thermalEdges.forEach {
            val t = Thread()
            t.run {
                it.queueMoveEnergy();
            }
            t.start()
            threadList.add(t)
        }
        threadList.forEach { it.join() }
        thermalEdges.forEach {
            it.clearRunBit()
        }
    }
}
