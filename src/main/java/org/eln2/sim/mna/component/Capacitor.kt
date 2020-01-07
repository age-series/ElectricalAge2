package org.eln2.sim.mna.component

import org.eln2.sim.mna.SubSystem
import org.eln2.sim.mna.misc.ISubSystemProcessI
import org.eln2.sim.mna.state.State

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

open class Capacitor: Bipole, ISubSystemProcessI {

    constructor()
    constructor(aPin: State?, bPin: State?): super(aPin, bPin)

    open var c: Double = 0.0
        set(c) {
            field = c
            dirty()
        }

    override fun getI(): Double {
        // TODO: Uhhhhhhhhhhh
        // No joke, this is what the old code did.
        return 0.0
    }

    override fun applyTo(s: SubSystem) {
        val cdt = c / s.dt
        s.addToA(aPin, aPin, cdt)
        s.addToA(aPin, bPin, -cdt)
        s.addToA(bPin, bPin, cdt)
        s.addToA(bPin, aPin, -cdt)
    }

    override fun simProcessI(s: SubSystem?) {
        if (s== null) return
        val cdt = c / s.dt
        val add: Double = (s.getXSafe(aPin) - s.getXSafe(bPin)) * cdt
        s.addToI(aPin, add)
        s.addToI(bPin, -add)
    }

    override fun quitSubSystem() {
        subSystem!!.removeProcess(this)
        super.quitSubSystem()
    }

    open fun getE(): Double {
        val u = u
        return u * u * c / 2
    }
}