package org.eln2.sim.mna.component

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */


import org.eln2.sim.mna.SubSystem
import org.eln2.sim.mna.misc.ISubSystemProcessI
import org.eln2.sim.mna.state.CurrentState
import org.eln2.sim.mna.state.State

open class Inductor: Bipole, ISubSystemProcessI {

    constructor()
    constructor(aPin: State?, bPin: State?): super(aPin, bPin)

    open var currentState: CurrentState = CurrentState()

    open var l: Double = 0.0
        set(l) {
            field = l
            dirty()
        }

    override fun getI(): Double {
        return currentState.state
    }

    open fun getE(): Double {
        val i = getI()
        return i * i * l / 2
    }

    override fun applyTo(s: SubSystem) {
        val ldt = -l / s.dt
        s.addToA(aPin, currentState, 1.0)
        s.addToA(bPin, currentState, -1.0)
        s.addToA(currentState, aPin, 1.0)
        s.addToA(currentState, bPin, -1.0)
        s.addToA(currentState, currentState, ldt)
    }

    override fun simProcessI(s: SubSystem) {
        val ldt = -l / s.dt
        s.addToI(currentState, ldt * currentState.state)
    }

    override fun quitSubSystem(s: SubSystem) {
        s.removeState(currentState)
        s.processI.remove(this)
        super.quitSubSystem(s)
    }

    override var subSystem: SubSystem? = null
        set(s) {
            field = s
            s?.addState(currentState)
            s?.processI?.add(this)
        }

    open fun resetStates() {
        currentState.state = 0.0
    }
}

/*
    @Override
    public void readFromNBT(NBTTagCompound nbt, String str) {
        str += name;
        currentState.state = (nbt.getDouble(str + "Istate"));
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt, String str) {
        str += name;
        nbt.setDouble(str + "Istate", currentState.state);
    }

     */