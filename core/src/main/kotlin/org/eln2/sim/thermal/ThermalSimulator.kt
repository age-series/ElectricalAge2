package org.eln2.sim.thermal
import org.eln2.sim.IProcess

/**
 * main thermal simulation class
 */
class ThermalSimulator: IProcess {
    var thermalElements = mutableSetOf<ThermalEdge>()
    override fun process(dt: Double) {
        val threadList = mutableSetOf<Thread>()
        thermalElements.forEach {
            val t = Thread()
            t.run {
                it.conductionNodes.forEach { tn ->
                    it.computeTransfer(tn.key, dt)
                }
            }
            t.start()
            threadList.add(t)
        }
        threadList.forEach { it.join() }
        thermalElements.forEach {
            it.clearRunBit()
        }
    }
}
