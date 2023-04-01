package org.eln2.mc.common.cells.foundation.behaviors

import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.sim.ThermalBody

fun interface IElectricalPowerAccessor {
    fun get(): Double
}

/**
 * Integrates electrical power into energy.
 * */
class ElectricalPowerConverterBehavior(private val accessor: IElectricalPowerAccessor): ICellBehavior {
    var energy: Double = 0.0
    var deltaEnergy: Double = 0.0

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

fun interface IThermalBodyAccessor {
    fun get(): ThermalBody
}

/**
 * Converts dissipated electrical energy to thermal energy.
 * */
@RequiresBehavior<ElectricalPowerConverterBehavior>
class ElectricalHeatTransferBehavior(private val thermalBodyAccessor: IThermalBodyAccessor) : ICellBehavior {
    private lateinit var converterBehavior: ElectricalPowerConverterBehavior

    override fun onAdded(container: CellBehaviorContainer) {
        converterBehavior = container.getBehavior()
    }

    override fun subscribe(subscribers: SubscriberCollection) {
        subscribers.addPreInstantaneous(this::simulationTick)
    }

    override fun destroy(subscribers: SubscriberCollection) {
        subscribers.removeSubscriber(this::simulationTick)
    }

    private fun simulationTick(dt: Double, p: SubscriberPhase){
        // Add delta energy

        thermalBodyAccessor.get().thermalEnergy += converterBehavior.deltaEnergy

        // Drain moved energy
        converterBehavior.energy -= converterBehavior.deltaEnergy
    }
}

fun CellBehaviorContainer.withElectricalPowerConverter(accessor: IElectricalPowerAccessor): CellBehaviorContainer{
    return this.add(ElectricalPowerConverterBehavior(accessor))
}

@RequiresBehavior<ElectricalPowerConverterBehavior>
fun CellBehaviorContainer.withElectricalHeatTransfer(getter: IThermalBodyAccessor): CellBehaviorContainer {
    return this.add(ElectricalHeatTransferBehavior(getter))
}
