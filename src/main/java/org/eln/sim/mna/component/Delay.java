package org.eln.sim.mna.component;

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln.sim.mna.SubSystem;
import org.eln.sim.mna.misc.ISubSystemProcessI;

public class Delay extends Bipole implements ISubSystemProcessI {

    double impedance, conductance;

    double oldIa, oldIb;

    public Delay set(double impedance) {
        this.impedance = impedance;
        this.conductance = 1 / impedance;
        return this;
    }

    @Override
    public void addedTo(SubSystem s) {
        super.addedTo(s);
        s.addProcess(this);
    }

    @Override
    public void applyTo(SubSystem s) {
        s.addToA(aPin, aPin, conductance);
        s.addToA(bPin, bPin, conductance);
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
        double iA = aPin.state * conductance + oldIa;
        double iB = bPin.state * conductance + oldIb;
        double iTarget = (iA - iB) / 2;

        double aPinI = iTarget - (aPin.state + bPin.state) * 0.5 * conductance;
        double bPinI = -iTarget - (aPin.state + bPin.state) * 0.5 * conductance;

        s.addToI(aPin, -aPinI);
        s.addToI(bPin, -bPinI);

        oldIa = aPinI;
        oldIb = bPinI;
    }

    @Override
    public double getCurrent() {
        return oldIa - oldIb;
    }
}
