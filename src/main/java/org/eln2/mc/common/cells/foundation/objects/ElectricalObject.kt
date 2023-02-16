package org.eln2.mc.common.cells.foundation.objects

import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Component
import org.eln2.mc.common.cells.foundation.CellBase

class ComponentInfo(val component: Component, val index: Int)

abstract class ElectricalObject : ISimulationObject {
    var circuit : Circuit? = null
        private set

    protected val connections = ArrayList<ElectricalObject>()

    final override val type: SimulationObjectType get() = SimulationObjectType.Electrical

    abstract fun offerComponent(neighbour: ElectricalObject): ComponentInfo

    fun setNewCircuit(circuit: Circuit){
        this.circuit = circuit

        registerComponents(circuit)
    }

    fun addConnection(other: ElectricalObject){
        connections.add(other)
    }

    override fun destroy() {
        connections.forEach {it.connections.remove(this) }
    }

    override fun update(connectionsChanged: Boolean, graphChanged: Boolean) {}

    override fun clear() {
        connections.clear()
        recreateComponents()
    }

    protected abstract fun recreateComponents()
    protected abstract fun registerComponents(circuit: Circuit)
}
