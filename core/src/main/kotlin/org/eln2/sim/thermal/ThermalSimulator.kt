package org.eln2.sim.thermal
import org.eln2.sim.IProcess

/**
 * main thermal simulation class
 */
class ThermalSimulator: IProcess {
    var thermalNodes = mutableSetOf<ThermalNode>()
    var thermalEdges = mutableSetOf<ThermalEdge>()
    override fun process(dt: Double) {
        val threadList = mutableSetOf<Thread>()
        thermalEdges.forEach {
            val t = Thread()
            t.run {
                it.queueMoveEnergy(dt);
            }
            t.start()
            threadList.add(t)
        }
        threadList.forEach { it.join() }
        threadList.clear()
        thermalNodes.forEach {
            val t = Thread()
            t.run {
                it.commitThermalChanges();
            }
            t.start()
            threadList.add(t)
        }
        threadList.forEach { it.join() }
        thermalEdges.forEach {
            it.clearRunBit()
        }
    }

    fun connectNodes(n1: ThermalNode, n2: ThermalNode, dir: Int, distance: Double) {
        if (thermalEdges.filter { it.thermalNodes == Pair(n1,n2) || it.thermalNodes == Pair(n2,n1) }.isEmpty().not()) return
        val sa1 = n1.surfaceArea[dir]!!
        val sa2 = n2.surfaceArea[6-dir]!!
        val sa = if (sa1 > sa2) { sa1 } else { sa2 }
        thermalEdges.add(ThermalEdge(Pair(n1,n2),sa,distance))
    }

    fun disconnectNodes(te: ThermalEdge) {
        te.thermalNodes.toList().forEach { it.thermalEdges.remove(te) }
        thermalEdges.remove(te)
    }

    fun disconnectNodes(n1: ThermalNode, n2: ThermalNode) {
        disconnectNodes(thermalEdges.filter {
            it.thermalNodes.toList().contains(n1) && it.thermalNodes.toList().contains(n2)
        }.first())
    }

    fun removeNodeFromNetwork(n: ThermalNode) {
        n.thermalEdges.forEach {
            it.thermalNodes.toList().filter { it != n }.first().thermalEdges.remove(it)
            thermalEdges.remove(it)
        }
        n.thermalEdges.clear()
        thermalNodes.remove(n)
    }
}
