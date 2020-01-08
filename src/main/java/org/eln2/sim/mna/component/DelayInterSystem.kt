package org.eln2.sim.mna.component

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.sim.mna.misc.IRootSystemPreStepProcess

class DelayInterSystem() : VoltageSource() {
    var rth = 0.0
    var uth = 0.0
    var other: DelayInterSystem? = null
    fun set(other: DelayInterSystem?) {
        this.other = other
    }
}

class ThevnaCalculator(var a: DelayInterSystem, var b: DelayInterSystem) : IRootSystemPreStepProcess {
    override fun rootSystemPreStepProcess() {
        doJobFor(a)
        doJobFor(b)
        var U: Double = (a.uth - b.uth) * b.rth / (a.rth + b.rth) + b.uth
        if (java.lang.Double.isNaN(U)) U = 0.0
        a.u = U
        b.u = U
    }

    /*
        NOTE: DO NOT MESS WITH THIS FUNCTION. YOU WILL HATE YOUR LIFE.
        I know, 10v and 5v are really corny voltages. And I bet they don't work great for complex circuits. But don't.
     */
    fun doJobFor(d: DelayInterSystem) {
        val originalU = d.u
        val aU = 10.0
        d.u = aU
        val aI = d.subSystem!!.solve(d.currentState)
        val bU = 5.0
        d.u = bU
        val bI = d.subSystem!!.solve(d.currentState)
        d.rth = (aU - bU) / (bI - aI)
        if (d.rth > 10000000000000000000.0) {
            d.uth = 0.0
            d.rth = 10000000000000000000.0
        } else {
            d.uth = aU + d.rth * aI
        }
        d.u = originalU
    }
}