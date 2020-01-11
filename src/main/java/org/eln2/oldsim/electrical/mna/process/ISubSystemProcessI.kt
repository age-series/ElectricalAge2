package org.eln2.oldsim.electrical.mna.process

import org.eln2.oldsim.electrical.mna.SubSystem

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

interface ISubSystemProcessI {
    fun simProcessI(s: SubSystem)
}