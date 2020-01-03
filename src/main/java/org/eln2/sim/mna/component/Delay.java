package org.eln2.sim.mna.component;

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.sim.mna.SubSystem;
import org.eln2.sim.mna.misc.ISubSystemProcessI;

public class Delay extends Bipole implements ISubSystemProcessI {

    double impedance, conductance;

    double oldIa, oldIb;

    public Delay set(double impedance) {
        this.impedance = impedance;
        this.conductance = 1 / impedance;
        return this;
    }

    @Override
    public void setSubSystem(SubSystem s) {
        super.setSubSystem(s);
        s.addProcess(this);
    }

    @Override
    public void applyTo(SubSystem s) {
        s.addToA(getAPin(), getAPin(), conductance);
        s.addToA(getBPin(), getBPin(), conductance);
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

    @Override
    public void simProcessI(SubSystem s) {
        double iA = getAPin().getState() * conductance + oldIa;
        double iB = getAPin().getState() * conductance + oldIb;
        double iTarget = (iA - iB) / 2;

        double aPinI = iTarget - (getAPin().getState() + getBPin().getState()) * 0.5 * conductance;
        double bPinI = -iTarget - (getAPin().getState() + getBPin().getState()) * 0.5 * conductance;

        s.addToI(getAPin(), -aPinI);
        s.addToI(getBPin(), -bPinI);

        oldIa = aPinI;
        oldIb = bPinI;
    }

    @Override
    public double getI() {
        return oldIa - oldIb;
    }
}
