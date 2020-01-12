package org.eln2.oldsim.electrical.parts

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

// NOTE: THIS CLASS SUCKS (I think - jrd)

import org.eln2.oldsim.electrical.mna.Th
import org.eln2.oldsim.electrical.mna.component.VoltageSource
import org.eln2.oldsim.electrical.interop.IRootSystemPreStepProcess
import oldsim.electrical.MnaConst
import org.eln2.oldsim.electrical.mna.state.State

open class PowerSourceBipole(val aPin: State?, val bPin: State?, val aSrc: VoltageSource, val bSrc: VoltageSource) : IRootSystemPreStepProcess {

    open var p = 0.0
    open var uMax = 0.0

    override fun rootSystemPreStepProcess() {
        val a = Th.getTh(aPin, aSrc)
        val b = Th.getTh(bPin, bSrc)
        if (java.lang.Double.isNaN(a?.U?: 0.0)) {
            a.U = 0.0
            a.R = MnaConst.highImpedance
        }
        if (java.lang.Double.isNaN(b.U)) {
            b.U = 0.0
            b.R = MnaConst.highImpedance
        }
        val Uth = a.U - b.U
        val Rth = a.R + b.R
        if (Uth >= uMax) {
            aSrc.u = a.U
            bSrc.u = b.U
        } else {
            var U = (Math.sqrt(Uth * Uth + 4 * p * Rth) + Uth) / 2
            U = Math.min(Math.min(U, uMax), Uth + Rth * uMax)
            if (java.lang.Double.isNaN(U)) U = 0.0
            val I = (Uth - U) / Rth
            aSrc.u = a.U - I * a.R
            bSrc.u = b.U + I * b.R
        }
    }
}

/*
    @Override
    public void readFromNBT(NBTTagCompound nbt, String str) {
        setP(nbt.getDouble(str + "P"));
        setUmax(nbt.getDouble(str + "Umax"));
        setImax(nbt.getDouble(str + "Imax"));
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt, String str) {
        nbt.setDouble(str + "P", getP());
        nbt.setDouble(str + "Umax", Umax);
        nbt.setDouble(str + "Imax", Imax);
    }*/