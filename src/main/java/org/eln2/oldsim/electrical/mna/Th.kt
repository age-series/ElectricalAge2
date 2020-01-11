package org.eln2.oldsim.electrical.mna

import org.eln2.oldsim.electrical.mna.component.VoltageSource
import org.eln2.oldsim.electrical.MnaConst
import org.eln2.oldsim.electrical.mna.state.State


class Th {
    var R = 0.0
    var U = 0.0
    val isHighImpedance: Boolean
        get() = R > 1e8

    companion object {
        fun getTh(d: State?, voltageSource: VoltageSource): Th {
            val th = Th()
            val originalU = d?.state?: 0.0
            val aU = 10.0
            voltageSource.u = aU
            val aI = voltageSource.subSystem?.solve(voltageSource.currentState)?: 0.0
            val bU = 5.0
            voltageSource.u = bU
            val bI = voltageSource.subSystem?.solve(voltageSource.currentState)?: 0.0
            var Rth = (aU - bU) / (bI - aI)
            val Uth: Double
            //if(Double.isInfinite(d.Rth)) d.Rth = Double.MAX_VALUE;
            if (Rth > 10000000000000000000.0 || Rth < 0) {
                Uth = 0.0
                Rth = 10000000000000000000.0
            } else {
                Uth = aU + Rth * aI
            }
            voltageSource.u = originalU
            th.R = Rth
            th.U = Uth
            if (java.lang.Double.isNaN(th.U)) {
                th.U = originalU
                th.R = MnaConst.highImpedance
            }
            if (java.lang.Double.isNaN(th.R)) {
                th.U = originalU
                th.R = MnaConst.highImpedance
            }
            return th
        }
    }
}