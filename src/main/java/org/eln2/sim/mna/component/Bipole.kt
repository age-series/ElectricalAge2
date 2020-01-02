package org.eln2.sim.mna.component

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.sim.mna.state.State

abstract class Bipole: Component {
    open var aPin: State? = null
    open var bPin: State? = null

    constructor()
    constructor(aPin: State?, bPin: State?) {
        connectTo(aPin, bPin)
    }

    open fun connectTo(aPin: State?, bPin: State?): Bipole {
        breakConnection()
        this.aPin = aPin
        this.bPin = bPin
        aPin?.add(this)
        bPin?.add(this)
        return this
    }

    open fun connectGhostTo(aPin: State?, bPin: State?): Bipole {
        breakConnection()
        this.aPin = aPin
        this.bPin = bPin
        return this
    }

    override fun breakConnection() {
        if (aPin != null) aPin!!.remove(this)
        if (bPin != null) bPin!!.remove(this)
    }

    override fun getConnectedStates(): Array<State?>? {
        return arrayOf(aPin, bPin)
    }

    abstract fun getCurrent(): Double

    open fun getU(): Double {
        return (aPin?.state?: 0.0) - (bPin?.state?: 0.0)
    }

    override fun toString(): String {
        return "[" + aPin + " " + this.javaClass.simpleName + " " + bPin + "]"
    }
}