package org.eln2.sim.mna.component

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.sim.mna.SubSystem
import org.eln2.sim.mna.misc.MnaConst
import org.eln2.sim.mna.state.CurrentState
import org.eln2.sim.mna.state.State

class Transformer: Bipole {

    constructor()
    constructor(aPin: State?, bPin: State?): super(aPin, bPin)

    open var aCurrentState = CurrentState()
    open var bCurrentState = CurrentState()
    open var ratio = 1.0

    private var r:Double = MnaConst.highImpedance

    fun getRInv(): Double {
        return 1.0 / r
    }

    override fun getI(): Double {
        return 0.0
    }

    override var subSystem: SubSystem? = null
        set(s) {
            field = s
            s?.addState(aCurrentState)
            s?.addState(bCurrentState)
        }

    override fun quitSubSystem(s: SubSystem) {
        s.removeState(aCurrentState)
        s.removeState(bCurrentState)
        super.quitSubSystem(s)
    }

    override fun applyTo(s: SubSystem) {
        s.addToA(bPin, bCurrentState, 1.0)
        s.addToA(bCurrentState, bPin, 1.0)
        s.addToA(bCurrentState, aPin, -ratio)
        s.addToA(aPin, aCurrentState, 1.0)
        s.addToA(aCurrentState, aPin, 1.0)
        s.addToA(aCurrentState, bPin, -1 / ratio)
        s.addToA(aCurrentState, aCurrentState, 1.0)
        s.addToA(aCurrentState, bCurrentState, ratio)
        s.addToA(bCurrentState, aCurrentState, 1.0)
        s.addToA(bCurrentState, bCurrentState, ratio)
    }
}