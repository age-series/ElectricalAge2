package org.eln2.oldsim.electrical.parts

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.oldsim.electrical.interop.InterSystem
import org.eln2.oldsim.electrical.mna.state.ElectricalLoad
import org.eln2.oldsim.electrical.mna.state.State

open class ElectricalConnection(open var l1: ElectricalLoad? = null, open var l2: ElectricalLoad? = null): InterSystem() {
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