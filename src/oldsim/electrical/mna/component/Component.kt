package org.eln2.oldsim.electrical.mna.component

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.oldsim.electrical.RootSystem
import org.eln2.oldsim.electrical.interop.IAbstractor
import org.eln2.oldsim.electrical.mna.SubSystem
import org.eln2.oldsim.electrical.mna.state.State
import java.lang.Exception

abstract class Component {
    open var name: String = ""
    open var subSystem: SubSystem? = null
        get() {
            return abstractedBy?.getAbstractorSubSystem()?: field
        }
    open var abstractedBy: IAbstractor? = null

    open fun addedTo(s: SubSystem) {
        this.subSystem = s
    }

    abstract fun applyTo(s: SubSystem)

    abstract fun getConnectedStates(): Array<State?>?

    open fun canBeReplacedByInterSystem() = false

    open fun breakConnection() {}

    open fun returnToRootSystem(root: RootSystem) {
        root.queuedComponents.add(this)
    }

    open fun dirty() {
        if (abstractedBy != null) {
            abstractedBy!!.dirty(this)
        } else subSystem?.matrixValid = false
    }

    open fun quitSubSystem(s: SubSystem) {
        if (s != subSystem) throw Exception("These subsystems are not the same!")
        subSystem = null
    }

    open fun isAbstracted(): Boolean {
        return abstractedBy != null
    }

    open fun onAddToRootSystem() {}

    open fun onRemovefromRootSystem() {}

    override fun toString(): String {
        return "(" + this.javaClass.simpleName + ")"
    }
}