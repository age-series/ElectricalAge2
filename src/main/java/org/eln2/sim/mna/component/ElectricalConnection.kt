package org.eln2.sim.mna.component

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.sim.mna.state.ElectricalLoad
import org.eln2.sim.mna.state.State

open class ElectricalConnection: InterSystem {

    constructor(l1: ElectricalLoad?, l2: ElectricalLoad?) {
        this.l1 = l1
        this.l2 = l2
    }

    open var l1: ElectricalLoad? = null
    open var l2: ElectricalLoad? = null

    fun notifyRsChange() {
        val R = (aPin as ElectricalLoad).r + (bPin as ElectricalLoad).r
        r = R
    }

    override fun onAddToRootSystem() {
        connectTo(l1 as State, l2 as State)
        notifyRsChange()
    }

    override fun onRemovefromRootSystem() {
        breakConnection()
    }
}