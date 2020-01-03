package org.eln2.sim.mna.component

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.sim.mna.SubSystem
import org.eln2.sim.mna.misc.ISubSystemProcessI
import org.eln2.sim.mna.state.State

open class VoltageSource: Bipole, ISubSystemProcessI {

    constructor()
    constructor(aPin: State?, bPin: State?): super(aPin, bPin)

    override var u: Double = 0.0
    open var currentState: State = State()

    open var p: Double
        get() {
            return u * getI()
        }
        set(p) {}

    override fun quitSubSystem() {
        subSystem?.states?.remove(currentState)
        subSystem?.removeProcess(this)
        super.quitSubSystem()
    }

    override var subSystem: SubSystem? = null
        set(s) {
            field = s
            s?.addState(currentState)
            s?.addProcess(this)
        }

    override fun applyTo(s: SubSystem) {
        s.addToA(aPin, currentState, 1.0)
        s.addToA(bPin, currentState, -1.0)
        s.addToA(currentState, aPin, 1.0)
        s.addToA(currentState, bPin, -1.0)
    }

    override fun simProcessI(s: SubSystem?) {
        s?.addToI(currentState, u)
    }

    override fun getI(): Double {
        return -currentState.state
    }
}

/*
@Override
public void readFromNBT(NBTTagCompound nbt, String str) {
    str += name;
    setU(nbt.getDouble(str + "U"));
    currentState.state = (nbt.getDouble(str + "Istate"));
}

@Override
public void writeToNBT(NBTTagCompound nbt, String str) {
    str += name;
    nbt.setDouble(str + "U", u);
    nbt.setDouble(str + "Istate", currentState.state);
}*/
