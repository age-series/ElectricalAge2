package org.eln2.mc.common.cells.foundation.objects

import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Component

class ComponentInfo(val component: Component, val index: Int)

/**
 * Represents an object that is part of an electrical simulation.
 * */
abstract class ElectricalObject : ISimulationObject {
    /**
     * The circuit this object is part of.
     * It is initialized while the solver is being built.
     * @see setNewCircuit
     * */
    var circuit: Circuit? = null
        private set

    protected val connections = ArrayList<ElectricalObject>()

    final override val type = SimulationObjectType.Electrical

    /**
     * Called by electrical objects to fetch a connection candidate.
     * */
    abstract fun offerComponent(neighbour: ElectricalObject): ComponentInfo

    /**
     * Called by the building logic when the electrical object is made part of a circuit.
     * Also calls the *registerComponents* method.
     * */
    fun setNewCircuit(circuit: Circuit) {
        this.circuit = circuit

        registerComponents(circuit)
    }

    /**
     * Called by the cell when a valid connection candidate is discovered.
     * */
    fun addConnection(other: ElectricalObject) {
        connections.add(other)
    }

    /**
     * Called when this object is destroyed. Connections are also cleaned up.
     * */
    override fun destroy() {
        connections.forEach { it.connections.remove(this) }
    }

    override fun update(connectionsChanged: Boolean, graphChanged: Boolean) {}

    override fun clear() {
        connections.clear()
        recreateComponents()
    }

    /**
     * Called when the solver is being built, and the components need to be re-created (or refreshed)
     * The connections are not available at this stage.
     * */
    protected abstract fun recreateComponents()

    /**
     * Called when the circuit must be updated with the components owned by this object.
     * */
    protected abstract fun registerComponents(circuit: Circuit)
}
