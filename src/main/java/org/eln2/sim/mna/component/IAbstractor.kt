package org.eln2.sim.mna.component

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.sim.mna.SubSystem

interface IAbstractor {
    fun dirty(component: Component?)
    fun getAbstractorSubSystem(): SubSystem?
}