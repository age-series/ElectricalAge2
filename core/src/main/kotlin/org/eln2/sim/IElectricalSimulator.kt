package org.eln2.sim

import org.eln2.sim.electrical.mna.component.Component

interface IElectricalSimulator {
    // TODO: This Pair garbage is a placeholder.
    var electricalConnections: MutableList<Pair<Component, Component>>
    var electricalProcess: MutableList<IProcess>
    var electricalRootProcess: MutableList<IProcess>
}
