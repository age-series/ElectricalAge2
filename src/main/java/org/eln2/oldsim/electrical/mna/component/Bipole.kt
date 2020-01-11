package org.eln2.oldsim.electrical.mna.component

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.oldsim.electrical.mna.state.State

abstract class Bipole: Component {
    open var aPin: State? = null
    open var bPin: State? = null

    open var u: Double
        set(u) {}
        get() {
            return (aPin?.state?: 0.0) - (bPin?.state?: 0.0)
        }

    constructor()
    constructor(aPin: State?, bPin: State?) {
        connectTo(aPin, bPin)
    }

    open fun connectTo(aPin: State?, bPin: State?): Bipole {
        breakConnection()
        this.aPin = aPin
        this.bPin = bPin
        aPin?.components?.add(this)
        bPin?.components?.add(this)
        return this
    }

    open fun connectGhostTo(aPin: State?, bPin: State?): Bipole {
        breakConnection()
        this.aPin = aPin
        this.bPin = bPin
        return this
    }

    override fun breakConnection() {
        aPin?.components?.remove(this)
        bPin?.components?.remove(this)
    }

    override fun getConnectedStates(): Array<State?>? {
        return arrayOf(aPin, bPin)
    }

    abstract fun getI(): Double

    override fun toString(): String {
        return "[" + aPin + " " + this.javaClass.simpleName + " " + bPin + "]"
    }
}