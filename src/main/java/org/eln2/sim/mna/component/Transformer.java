package org.eln2.sim.mna.component;

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.sim.mna.SubSystem;
import org.eln2.sim.mna.misc.MnaConst;
import org.eln2.sim.mna.state.CurrentState;
import org.eln2.sim.mna.state.State;

public class Transformer extends Bipole {

    public Transformer() {
    }

    public CurrentState aCurrentState = new CurrentState();
    public CurrentState bCurrentState = new CurrentState();

    public Transformer(State aPin, State bPin) {
        super(aPin, bPin);
    }

    double ratio = 1;

    public void setRatio(double ratio) {
        this.ratio = ratio;
    }

    public double getRatio() {
        return ratio;
    }

    private double r = MnaConst.highImpedance, rInv = 1 / MnaConst.highImpedance;

    @Override
    public void quitSubSystem() {
        getSubSystem().states.remove(aCurrentState);
        getSubSystem().states.remove(bCurrentState);
        super.quitSubSystem();
    }

    @Override
    public void setSubSystem(SubSystem s) {
        super.setSubSystem(s);
        s.addState(aCurrentState);
        s.addState(bCurrentState);
    }

    @Override
    public void applyTo(SubSystem s) {
        s.addToA(getBPin(), bCurrentState, 1.0);
        s.addToA(bCurrentState, getBPin(), 1.0);
        s.addToA(bCurrentState, getAPin(), -ratio);

        s.addToA(getAPin(), aCurrentState, 1.0);
        s.addToA(aCurrentState, getAPin(), 1.0);
        s.addToA(aCurrentState, getBPin(), -1 / ratio);

        s.addToA(aCurrentState, aCurrentState, 1.0);
        s.addToA(aCurrentState, bCurrentState, ratio);
        s.addToA(bCurrentState, aCurrentState, 1.0);
        s.addToA(bCurrentState, bCurrentState, ratio);
    }


    @Override
    public double getCurrent() {
        return 0;

    }
}
