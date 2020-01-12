package org.eln2.oldsim.electrical.interop

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.oldsim.electrical.mna.component.VoltageSource
import org.eln2.oldsim.electrical.mna.state.State

class TransformerInterSystemProcess(val aState: State, val bState: State, val aSource: VoltageSource, val bSource: VoltageSource): IRootSystemPreStepProcess {

    var ratio = 1.0

    override fun rootSystemPreStepProcess() {
        val a = getTh(aState, aSource)
        val b = getTh(bState, bSource)
        var aU = (a.U * b.R + ratio * b.U * a.R) / (b.R + ratio * ratio * a.R)
        if (java.lang.Double.isNaN(aU)) {
            aU = 0.0
        }
        aSource.u = aU
        bSource.u = aU * ratio
    }

    class Th {
        var R = 0.0
        var U = 0.0
    }

    fun getTh(d: State, voltageSource: VoltageSource): Th {
        val th = Th()
        val originalU = d.state
        val aU = 10.0
        voltageSource.u = aU
        val aI = d.subSystem!!.solve(voltageSource.currentState)
        val bU = 5.0
        voltageSource.u = bU
        val bI = d.subSystem!!.solve(voltageSource.currentState)
        var Rth = (aU - bU) / (bI - aI)
        val Uth: Double
        //if (Double.isInfinite(d.Rth)) d.Rth = Double.MAX_VALUE;
        if (Rth > 10000000000000000000.0 || Rth < 0) {
            Uth = 0.0
            Rth = 10000000000000000000.0
        } else {
            Uth = aU + Rth * aI
        }
        voltageSource.u = originalU
        th.R = Rth
        th.U = Uth
        return th
    }
}