package org.eln2.mc.common.cells.foundation.objects

/**
 * Represents a discrete simulation unit that participates in one simulation type.
 * */
interface ISimulationObject {
    val type: SimulationObjectType

    /**
     * Called when the connections and/or graph changes.
     * */
    fun update(connectionsChanged: Boolean, graphChanged: Boolean)

    /**
     * Called when the solver is being built.
     * Here, the previous state should be cleared so that the object is ready to join a new simulation.
     * */
    fun clear()

    /**
     * Called when the solver is being built, after *clear*.
     * */
    fun build()

    /**
     * Called when the cell is destroyed.
     * Connections should be removed here.
     * */
    fun destroy()
}
