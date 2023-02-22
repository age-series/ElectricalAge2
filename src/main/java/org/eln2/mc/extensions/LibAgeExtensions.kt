package org.eln2.mc.extensions

import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Component
import org.eln2.mc.common.cells.foundation.ComponentHolder
import org.eln2.mc.common.cells.foundation.objects.ElectricalComponentInfo

object LibAgeExtensions {
    fun Component.connect(pin: Int, info: ElectricalComponentInfo){
        this.connect(pin, info.component, info.index)
    }

    fun Circuit.add(holder: ComponentHolder<*>){
        this.add(holder.instance)
    }
}
