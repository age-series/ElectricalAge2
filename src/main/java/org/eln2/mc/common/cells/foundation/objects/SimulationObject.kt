package org.eln2.mc.common.cells.foundation.objects

import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.LocationDescriptor

/**
 * Represents a discrete simulation unit that participates in one simulation type.
 * */
abstract class SimulationObject(val cell: CellBase) {
    abstract val type: SimulationObjectType

    /**
     * Called when the connections and/or graph changes.
     * */
    abstract fun update(connectionsChanged: Boolean, graphChanged: Boolean)

    /**
     * Called when the solver is being built.
     * Here, the previous state should be cleared so that the object is ready to join a new simulation.
     * */
    abstract fun clear()

    /**
     * Called when the solver is being built, after *clear*.
     * */
    abstract fun build()

    /**
     * Called when the cell is destroyed.
     * Connections should be removed here.
     * */
    abstract fun destroy()

    abstract fun acceptsRemote(remoteDesc: LocationDescriptor): Boolean
}
