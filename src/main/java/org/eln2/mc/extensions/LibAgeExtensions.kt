package org.eln2.mc.extensions

import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Component
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.ageseries.libage.sim.electrical.mna.component.VoltageSource
import org.eln2.mc.common.cells.foundation.ComponentHolder
import org.eln2.mc.common.cells.foundation.objects.ElectricalComponentInfo
import kotlin.math.abs

object LibAgeExtensions {
    private const val EPSILON = 0.001

    fun Component.connect(pin: Int, info: ElectricalComponentInfo){
        this.connect(pin, info.component, info.index)
    }

    fun Circuit.add(holder: ComponentHolder<*>){
        this.add(holder.instance)
    }

    fun Resistor.setResistanceEpsilon(resistance: Double, epsilon: Double = EPSILON): Boolean {
        if(abs(this.resistance - resistance) < epsilon){
            return false
        }

        this.resistance = resistance

        return true
    }

    fun VoltageSource.setPotentialEpsilon(potential: Double, epsilon: Double = EPSILON): Boolean {
        if(abs(this.potential - potential) < epsilon){
            return false
        }

        this.potential = potential

        return true
    }
}
