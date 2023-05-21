package org.eln2.mc.common.cells.foundation.objects

import org.ageseries.libage.sim.thermal.ConnectionParameters
import org.ageseries.libage.sim.thermal.Simulator
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.sim.ThermalBody

data class ThermalComponentInfo(val body: ThermalBody)

abstract class ThermalObject(cell: CellBase) : SimulationObject(cell) {
    var simulation: Simulator? = null
        private set

    protected val connections = ArrayList<ThermalObject>()

    final override val type = SimulationObjectType.Thermal

    open val maxConnections = Int.MAX_VALUE

    protected fun indexOf(obj: ThermalObject): Int {
        val index = connections.indexOf(obj)

        if (index == -1) {
            error("Connections did not have $obj")
        }

        return index
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
    open fun addConnection(connectionInfo: ThermalObject) {
        require(!connections.contains(connectionInfo)) { "Duplicate connection" }
        connections.add(connectionInfo)

        if(connections.size > maxConnections){
            error("Thermal object received more connections than were allowed")
        }
    }

    /**
     * Called when this object is destroyed. Connections are also cleaned up.
     * */
    override fun destroy() {
        println()
        connections.forEach { it.connections.remove(this) }
    }

    override fun update(connectionsChanged: Boolean, graphChanged: Boolean) {}

    override fun clear() {
        connections.clear()
    }

    override fun build() {
        if(simulation == null){
            error("Tried to build thermal obj with null simulation")
        }

        connections.forEach { remote ->
            assert(remote.simulation == simulation)

            simulation!!.connect(
                this.offerComponent(remote).body.thermalMass,
                remote.offerComponent(this).body.thermalMass,
                ConnectionParameters.DEFAULT
            )
        }
    }

    /**
     * Called when the simulation must be updated with the components owned by this object.
     * */
    protected abstract fun addComponents(simulator: Simulator)
}
