package org.eln2.mc.extensions

import org.ageseries.libage.sim.electrical.mna.component.Component
import org.eln2.mc.common.cell.ElectricalComponentConnection

object ComponentExtensions {
    fun Component.connectToPinOf(localIndex : Int, remoteInfo : ElectricalComponentConnection){
        this.connect(localIndex, remoteInfo.component, remoteInfo.index)
    }
}
