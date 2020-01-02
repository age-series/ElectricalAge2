package org.eln2.sim.mna.component;

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.sim.mna.SubSystem;
import org.eln2.sim.mna.misc.ISubSystemProcessI;
import org.eln2.sim.mna.state.State;

public class Capacitor extends Bipole implements ISubSystemProcessI {

    private double c = 0;
    double cdt;

    public Capacitor() {
    }

    public Capacitor(State aPin, State bPin) {
        connectTo(aPin, bPin);
    }

    @Override
    public double getCurrent() {
        return 0;
    }

    public void setC(double c) {
        this.c = c;
        dirty();
    }

    @Override
    public void applyTo(SubSystem s) {
        cdt = c / s.getDt();

        s.addToA(getAPin(), getAPin(), cdt);
        s.addToA(getAPin(), getBPin(), -cdt);
        s.addToA(getBPin(), getBPin(), cdt);
        s.addToA(getBPin(), getAPin(), -cdt);
    }

    @Override
    public void simProcessI(SubSystem s) {
        double add = (s.getXSafe(getAPin()) - s.getXSafe(getBPin())) * cdt;
        s.addToI(getAPin(), add);
        s.addToI(getBPin(), -add);
    }

    @Override
    public void quitSubSystem() {
        getSubSystem().removeProcess(this);
        super.quitSubSystem();
    }

    @Override
    public void setSubSystem(SubSystem s) {
        super.setSubSystem(s);
        s.addProcess(this);
    }

    public double getE() {
        double u = getU();
        return u * u * c / 2;
    }

    public double getC() {
        return c;
    }
}
