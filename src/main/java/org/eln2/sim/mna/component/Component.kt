package org.eln2.sim.mna.component

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.sim.mna.RootSystem
import org.eln2.sim.mna.SubSystem
import org.eln2.sim.mna.state.State

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
        root.addComponents.add(this)
    }

    open fun dirty() {
        if (abstractedBy != null) {
            abstractedBy!!.dirty(this)
        } else subSystem?.invalidate()
    }

    open fun quitSubSystem() {
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