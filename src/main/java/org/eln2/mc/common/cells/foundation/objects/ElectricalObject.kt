package org.eln2.mc.common.cells.foundation.objects

import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Component
import org.eln2.mc.common.cells.foundation.CellBase

data class ElectricalComponentInfo(val component: Component, val index: Int)

/**
 * Represents an object that is part of an electrical simulation.
 * */
abstract class ElectricalObject(cell: CellBase) : SimulationObject(cell) {
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
     * This is used to validate new connections.
     * If more connections than what is specified are created, an error will occur.
     * */
    open val maxConnections = Int.MAX_VALUE

    protected fun indexOf(obj: ElectricalObject): Int {
        val index = connections.indexOf(obj)

        if (index == -1) {
            error("Connections did not have $obj")
        }

        return index
    }

    /**
     * Called by electrical objects to fetch a connection candidate.
     * */
    abstract fun offerComponent(neighbour: ElectricalObject): ElectricalComponentInfo

    /**
     * Called by the building logic when the electrical object is made part of a circuit.
     * Also calls the *registerComponents* method.
     * */
    fun setNewCircuit(circuit: Circuit) {
        this.circuit = circuit

        addComponents(circuit)
    }

    /**
     * Called by the cell when a valid connection candidate is discovered.
     * */
    open fun addConnection(remoteObj: ElectricalObject) {
        require(!connections.contains(remoteObj)) { "Duplicate connection" }

        connections.add(remoteObj)

        if(connections.size > maxConnections){
            error("Electrical object received more connections than were allowed")
        }
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
        clearComponents()
    }

    /**
     * Called when the solver is being built, and the components need to be re-created (or refreshed)
     * The connections are not available at this stage.
     * */
    protected abstract fun clearComponents()

    /**
     * Called when the circuit must be updated with the components owned by this object.
     * */
    protected abstract fun addComponents(circuit: Circuit)
}
