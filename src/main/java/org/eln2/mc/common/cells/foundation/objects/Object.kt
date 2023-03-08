package org.eln2.mc.common.cells.foundation.objects

import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Component
import org.ageseries.libage.sim.thermal.ConnectionParameters
import org.ageseries.libage.sim.thermal.Simulator
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.space.RelativeRotationDirection
import org.eln2.mc.sim.ThermalBody

data class ElectricalComponentInfo(val component: Component, val index: Int)
data class ElectricalConnectionInfo(val obj: ElectricalObject, val direction: RelativeRotationDirection)

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

    protected val connections = ArrayList<ElectricalConnectionInfo>()

    final override val type = SimulationObjectType.Electrical

    /**
     * This is used to validate new connections.
     * If more connections than what is specified are created, an error will occur.
     * */
    open val maxConnections = Int.MAX_VALUE

    protected fun indexOf(obj: ElectricalObject): Int {
        val index = connections.indexOfFirst { it.obj == obj }

        if (index == -1) {
            error("Connections did not have $obj")
        }

        return index
    }

    protected fun directionOf(obj: ElectricalObject): RelativeRotationDirection {
        return connections[indexOf(obj)].direction
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
    open fun addConnection(connectionInfo: ElectricalConnectionInfo) {
        if (connections.contains(connectionInfo)) {
            error("Duplicate connection")
        }

        connections.add(connectionInfo)

        if(connections.size > maxConnections){
            error("Electrical object received more connections than were allowed")
        }
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

data class ThermalComponentInfo(val body: ThermalBody)
data class ThermalConnectionInfo(val obj: ThermalObject, val direction: RelativeRotationDirection)

abstract class ThermalObject : ISimulationObject {
    var simulation: Simulator? = null
        private set

    protected val connections = ArrayList<ThermalConnectionInfo>()

    final override val type = SimulationObjectType.Thermal

    open val maxConnections = Int.MAX_VALUE

    protected fun indexOf(obj: ThermalObject): Int {
        val index = connections.indexOfFirst { it.obj == obj }

        if (index == -1) {
            error("Connections did not have $obj")
        }

        return index
    }

    protected fun directionOf(obj: ThermalObject): RelativeRotationDirection {
        return connections[indexOf(obj)].direction
    }

    /**
     * Called by the cell graph to fetch a connection candidate.
     * */
    abstract fun offerComponent(neighbour: ThermalObject): ThermalComponentInfo

    /**
     * Called by the building logic when the thermal object is made part of a simulation.
     * Also calls the *registerComponents* method.
     * */
    fun setNewSimulation(simulator: Simulator) {
        this.simulation = simulator

        addComponents(simulator)
    }

    /**
     * Called by the cell when a valid connection candidate is discovered.
     * */
    open fun addConnection(connectionInfo: ThermalConnectionInfo) {
        if (connections.contains(connectionInfo)) {
            error("Duplicate connection")
        }

        connections.add(connectionInfo)

        if(connections.size > maxConnections){
            error("Thermal object received more connections than were allowed")
        }
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
    }

    override fun build() {
        if(simulation == null){
            error("Tried to build thermal obj with null simulation")
        }

        connections.forEach { connection ->
            val remote = connection.obj

            assert(remote.simulation == simulation)

            simulation!!.connect(
                this.offerComponent(remote).body.mass,
                remote.offerComponent(this).body.mass,
                ConnectionParameters.DEFAULT)
        }
    }

    /**
     * Called when the simulation must be updated with the components owned by this object.
     * */
    protected abstract fun addComponents(simulator: Simulator)
}
