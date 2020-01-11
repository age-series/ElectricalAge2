package org.eln2.oldsim.electrical.interop

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.oldsim.electrical.mna.SubSystem
import org.eln2.oldsim.electrical.mna.component.Component

interface IAbstractor {
    fun dirty(component: Component?)
    fun getAbstractorSubSystem(): SubSystem?
}