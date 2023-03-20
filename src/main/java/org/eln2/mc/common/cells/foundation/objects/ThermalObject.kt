package org.eln2.mc.common.cells.foundation.objects

import org.ageseries.libage.sim.thermal.ConnectionParameters
import org.ageseries.libage.sim.thermal.Simulator
import org.eln2.mc.common.space.DirectionMask
import org.eln2.mc.common.space.RelativeRotationDirection
import org.eln2.mc.sim.ThermalBody

data class ThermalComponentInfo(val body: ThermalBody)
data class ThermalConnectionInfo(val obj: ThermalObject, val direction: RelativeRotationDirection)

abstract class ThermalObject : ISimulationObject {
    override val connectionMask: DirectionMask = DirectionMask.HORIZONTALS

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
                ConnectionParameters.DEFAULT
            )
        }
    }

    /**
     * Called when the simulation must be updated with the components owned by this object.
     * */
    protected abstract fun addComponents(simulator: Simulator)
}
