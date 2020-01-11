package org.eln2.sim.mna.component

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.sim.mna.SubSystem
import org.eln2.sim.mna.Th
import org.eln2.sim.mna.misc.IRootSystemPreStepProcess
import org.eln2.sim.mna.state.State

class PowerSource: VoltageSource, IRootSystemPreStepProcess {

    constructor()
    constructor(aPin: State?, bPin: State?): super(aPin, bPin)

    override var p: Double = 0.0

    open var IMax: Double = 0.0
    open var UMax: Double = 0.0

    override var subSystem: SubSystem? = null
        set(s) {
            field = s
            s?.rootSystem?.queuedProcessPre?.add(this)
            s?.processI?.add(this)
        }

    override fun quitSubSystem(s: SubSystem) {
        s.rootSystem?.queuedProcessPre?.remove(this)
        super.quitSubSystem(s)
    }

    override fun rootSystemPreStepProcess() {
        val aPinCached = aPin
        val t = Th.getTh(aPinCached, this)
        var U = (Math.sqrt(t.U * t.U + 4 * p * t.R) + t.U) / 2
        U = Math.min(Math.min(U, UMax), t.U + t.R * IMax)
        if (java.lang.Double.isNaN(U)) U = 0.0
        if (U < t.U) U = t.U
        u = U
    }

    fun getEffectiveP(): Double {
        return u * getI()
    }

}

/*
    @Override
    public void readFromNBT(NBTTagCompound nbt, String str) {
        super.readFromNBT(nbt, str);

        str += name;

        setP(nbt.getDouble(str + "P"));
        setUmax(nbt.getDouble(str + "Umax"));
        setImax(nbt.getDouble(str + "Imax"));
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt, String str) {
        super.writeToNBT(nbt, str);

        str += name;

        nbt.setDouble(str + "P", getP());
        nbt.setDouble(str + "Umax", Umax);
        nbt.setDouble(str + "Imax", Imax);
    }
*/