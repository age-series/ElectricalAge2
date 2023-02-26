package org.eln2.mc.common.cells.foundation.behaviors

import org.eln2.mc.common.cells.foundation.CellBehaviorContainer
import org.eln2.mc.common.cells.foundation.ICellBehavior
import org.eln2.mc.common.cells.foundation.SubscriberCollection
import org.eln2.mc.common.cells.foundation.SubscriberPhase

fun interface IElectricalPowerAccessor {
    fun get(): Double
}

/**
 * Integrates electrical power into energy.
 * */
class ElectricalEnergyConverterBehavior(private val accessor: IElectricalPowerAccessor): ICellBehavior {
    var energy: Double = 0.0
        private set

    var deltaEnergy: Double = 0.0
        private set

    override fun onAdded(container: CellBehaviorContainer) {}

    override fun subscribe(subscribers: SubscriberCollection) {
        subscribers.addPreInstantaneous(this::simulationTick)
    }

    override fun destroy(subscribers: SubscriberCollection) {
        subscribers.removeSubscriber(this::simulationTick)
    }

    private fun simulationTick(dt: Double, p: SubscriberPhase){
        deltaEnergy = accessor.get() * dt
        energy += deltaEnergy
    }
}

object Extensions {
    fun CellBehaviorContainer.withElectricalEnergyConverter(accessor: IElectricalPowerAccessor): CellBehaviorContainer{
        return this.add(ElectricalEnergyConverterBehavior(accessor))
    }
}
