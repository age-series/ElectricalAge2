package org.eln2.mc.extensions

import org.ageseries.libage.sim.electrical.mna.component.Component
import org.eln2.mc.common.cells.foundation.ComponentInfo

object ComponentExtensions {
    fun Component.connectToPinOf(localIndex : Int, remoteInfo : ComponentInfo){
        this.connect(localIndex, remoteInfo.component, remoteInfo.index)
    }
}
