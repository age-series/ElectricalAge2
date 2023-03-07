package org.eln2.mc.common.cells.foundation.behaviors

import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.sim.ThermalBody

fun interface IElectricalPowerAccessor {
    fun get(): Double
}

/**
 * Integrates electrical power into energy.
 * */
class ElectricalEnergyConverterBehavior(private val accessor: IElectricalPowerAccessor): ICellBehavior {
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

fun interface IThermalBodyGetter {
    fun get(): ThermalBody<CellPos>
}

/**
 * Converts dissipated electrical energy to thermal energy.
 * */
@RequiresBehavior<ElectricalEnergyConverterBehavior>
class JouleEffectBehavior(private val thermalBodyAccessor: IThermalBodyGetter) : ICellBehavior {
    private lateinit var converterBehavior: ElectricalEnergyConverterBehavior

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

object Extensions {
    fun CellBehaviorContainer.withElectricalEnergyConverter(accessor: IElectricalPowerAccessor): CellBehaviorContainer{
        return this.add(ElectricalEnergyConverterBehavior(accessor))
    }

    @RequiresBehavior<ElectricalEnergyConverterBehavior>
    fun CellBehaviorContainer.withJouleEffectHeating(getter: IThermalBodyGetter): CellBehaviorContainer {
        return this.add(JouleEffectBehavior(getter))
    }
}
