package org.eln2.mc.common.cells.foundation.objects

interface IElectricalObject : ISimulationObject {
    override val type: SimulationObjectType get() = SimulationObjectType.Electrical
}
