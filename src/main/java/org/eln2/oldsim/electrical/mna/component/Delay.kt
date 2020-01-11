package org.eln2.oldsim.electrical.mna.component

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.oldsim.electrical.mna.SubSystem
import org.eln2.oldsim.electrical.mna.process.ISubSystemProcessI
import org.eln2.oldsim.electrical.mna.state.State

open class Delay: Bipole, ISubSystemProcessI {

    constructor()
    constructor(aPin: State?, bPin: State?): super(aPin, bPin)

    open var impedance = 0.0
    open var conductance = 0.0

    open var oldIa = 0.0
    open var oldIb = 0.0

    open fun set(impedance: Double): Delay {
        this.impedance = impedance
        conductance = 1 / impedance
        return this
    }

    override var subSystem: SubSystem? = null
        set(s) {
            field =s
            s?.processI?.add(this)
        }

    override fun applyTo(s: SubSystem) {
        s.addToA(aPin, aPin, conductance)
        s.addToA(bPin, bPin, conductance)
    }


    /*@Override
    public void simProcessI(SubSystem s) {
		double aPinI = 2 * s.getX(bPin) * conductance + oldIb;
		double bPinI = 2 * s.getX(aPin) * conductance + oldIa;

		s.addToI(aPin, aPinI);
		s.addToI(bPin, bPinI);

		oldIa = -aPinI;
		oldIb = -bPinI;
	}*/
    override fun simProcessI(s: SubSystem) {
        val iA = aPin!!.state * conductance + oldIa
        val iB = aPin!!.state * conductance + oldIb
        val iTarget = (iA - iB) / 2
        val aPinI = iTarget - (aPin!!.state + bPin!!.state) * 0.5 * conductance
        val bPinI = -iTarget - (aPin!!.state + bPin!!.state) * 0.5 * conductance
        s.addToI(aPin, -aPinI)
        s.addToI(bPin, -bPinI)
        oldIa = aPinI
        oldIb = bPinI
    }

    override fun getI(): Double {
        return oldIa - oldIb
    }
}