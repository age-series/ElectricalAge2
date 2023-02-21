package org.eln2.mc.extensions

import org.ageseries.libage.sim.electrical.mna.component.Component
import org.eln2.mc.common.cells.foundation.objects.ElectricalComponentInfo

object LibAgeExtensions {
    fun Component.connect(pin: Int, info: ElectricalComponentInfo){
        this.connect(pin, info.component, info.index)
    }
}
