package org.eln2.mc.common.cells.foundation.objects

interface ISimulationObject {
    val type: SimulationObjectType

    fun update(connectionsChanged: Boolean, graphChanged: Boolean)
    fun clear()
    fun build()
    fun destroy()
}
