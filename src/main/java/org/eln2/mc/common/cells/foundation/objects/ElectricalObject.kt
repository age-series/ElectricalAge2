package org.eln2.mc.common.cells.foundation.objects

import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Component
import org.eln2.mc.Eln2
import org.eln2.mc.common.space.RelativeRotationDirection

data class ComponentInfo(val component: Component, val index: Int)
data class ConnectionInfo(val obj: ElectricalObject, val direction: RelativeRotationDirection)

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

    protected val connections = ArrayList<ConnectionInfo>()

    final override val type = SimulationObjectType.Electrical

    protected fun indexOf(obj: ElectricalObject): Int {
        val index = connections.indexOfFirst { it.obj == obj }

        if(index == -1){
            error("Connections did not have $obj")
        }

        return index
    }

    protected fun directionOf(obj: ElectricalObject): RelativeRotationDirection{
        val index = indexOf(obj)

        return connections[index].direction
    }

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
    fun addConnection(connectionInfo: ConnectionInfo) {
        if(connections.contains(connectionInfo)){
            error("Duplicate connection")
        }

        connections.add(connectionInfo)

        Eln2.LOGGER.info("Recorded connection on ${connectionInfo.direction} with ${connectionInfo.obj}")
    }

    /**
     * Called when this object is destroyed. Connections are also cleaned up.
     * */
    override fun destroy() {
        connections.forEach { it.obj.connections.removeAll { conn -> conn.obj == this } }
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
