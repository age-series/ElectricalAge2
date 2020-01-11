package org.eln2.sim.mna.state

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.sim.mna.RootSystem
import org.eln2.sim.mna.SubSystem
import org.eln2.sim.mna.component.Component
import org.eln2.sim.mna.component.IAbstractor
import java.util.*

open class State {

    open var id = -1

    open var state: Double = 0.0

    open var subSystem: SubSystem? = null
        get() {
            return abstractedBy?.getAbstractorSubSystem()?: field
        }

    open var components = mutableListOf<Component>()

    open var abstractedBy: IAbstractor? = null

    open var privateSubSystem: Boolean = false

    open var mustBeFarFromInterSystem: Boolean = false

    open var canBeSimplifiedByLine = false

    fun quitSubSystem() {
        subSystem = null
    }

    fun getComponentsNotAbstracted(): ArrayList<Component>? {
        return ArrayList(components.filter {
            !it.isAbstracted()
        })
    }

    fun addedTo(s: SubSystem?) {
        this.subSystem = s
    }

    fun returnToRootSystem(root: RootSystem) {
        root.queuedStates.add(this)
    }

    fun getNotSimulated(): Boolean {
        return subSystem == null && abstractedBy == null;
    }

    override fun toString(): String {
        return "(${this.id}, ${this.state})"
        //return "(" + this.id + "," + this.javaClass.simpleName + ")"
    }
}

open class VoltageState : State() {

    open var u: Double
        get() {
            return state
        }
        set(s) {
            state = s
        }

    override fun toString(): String {
        return "(${this.id}, ${this.state}V)"
        //return "(" + this.id + "," + this.javaClass.simpleName + ")"
    }
}

open class CurrentState : State() {
    override fun toString(): String {
        return "(${this.id}, ${this.state}A)"
        //return "(" + this.id + "," + this.javaClass.simpleName + ")"
    }
}