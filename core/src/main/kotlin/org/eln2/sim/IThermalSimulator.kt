package org.eln2.sim

import org.eln2.sim.thermal.ThermalEdge
import org.eln2.sim.thermal.ThermalNode

interface IThermalSimulator {
    // TODO: This Pair garbage is a placeholder.
    var thermalConnections: MutableList<Pair<ThermalNode, ThermalNode>>
    var thermalNodeList: MutableList<ThermalNode>
    var thermalEdgeList: MutableList<ThermalEdge>
    var thermalSlowProcess: MutableList<IProcess>
    var thermalFastProcess: MutableList<IProcess>
}
