package org.eln2.mc.extensions

import org.eln2.libelectric.sim.electrical.mna.component.Component
import org.eln2.mc.common.cell.ComponentInfo

object ComponentExtensions {
    fun Component.connectToPinOf(localIndex : Int, remoteInfo : ComponentInfo){
        this.connect(localIndex, remoteInfo.component, remoteInfo.index)
    }
}
