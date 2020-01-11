package org.eln2.oldsim.electrical.mna.component

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.oldsim.electrical.mna.SubSystem
import org.eln2.oldsim.electrical.MnaConst
import org.eln2.oldsim.electrical.mna.state.State

open class Resistor: Bipole {

    constructor()
    constructor(aPin: State?, bPin: State?): super(aPin, bPin)

    open var r: Double = MnaConst.highImpedance
        get() = field
        set(r) {
            if (field != r) {
                field = r
                dirty()
            }
        }

    fun getRInv(): Double {
        return 1.0 / r
    }

    override fun getI(): Double {
        return u / r
    }

    fun getP(): Double {
        return u * getI()
    }

    fun highImpedance(): Resistor {
        r = MnaConst.highImpedance
        return this
    }

    fun ultraImpedance(): Resistor {
        r = MnaConst.ultraImpedance
        return this
    }

    fun pullDown(): Resistor {
        r = MnaConst.pullDown
        return this
    }

    fun canBridge(): Boolean {
        return false
    }

    override fun applyTo(s: SubSystem) {
        s.addToA(aPin, aPin, getRInv())
        s.addToA(aPin, bPin, -getRInv())
        s.addToA(bPin, bPin, getRInv())
        s.addToA(bPin, aPin, -getRInv())
    }

}