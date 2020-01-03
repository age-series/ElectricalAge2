package org.eln2.sim.mna.component;

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.sim.mna.SubSystem;
import org.eln2.sim.mna.misc.ISubSystemProcessI;
import org.eln2.sim.mna.state.CurrentState;
import org.eln2.sim.mna.state.State;

public class Inductor extends Bipole implements ISubSystemProcessI {//, INBTTReady {

    String name;

    private double l = 0;
    double ldt;

    private CurrentState currentState = new CurrentState();

    public Inductor(String name) {
        this.name = name;
    }

    public Inductor(String name, State aPin, State bPin) {
        super(aPin, bPin);
        this.name = name;
    }

    @Override
    public double getI() {
        return currentState.getState();
    }

    public double getL() {
        return l;
    }

    public void setL(double l) {
        this.l = l;
        dirty();
    }

    public double getE() {
        final double i = getI();
        return i * i * l / 2;
    }

    @Override
    public void applyTo(SubSystem s) {
        ldt = -l / s.getDt();

        s.addToA(getAPin(), currentState, 1);
        s.addToA(getBPin(), currentState, -1);
        s.addToA(currentState, getAPin(), 1);
        s.addToA(currentState, getBPin(), -1);
        s.addToA(currentState, currentState, ldt);
    }

    @Override
    public void simProcessI(SubSystem s) {
        s.addToI(currentState, ldt * currentState.getState());
    }

    @Override
    public void quitSubSystem() {
        getSubSystem().states.remove(getCurrentState());
        getSubSystem().removeProcess(this);
        super.quitSubSystem();
    }

    @Override
    public void setSubSystem(SubSystem s) {
        super.setSubSystem(s);
        s.addState(getCurrentState());
        s.addProcess(this);
    }

    public CurrentState getCurrentState() {
        return currentState;
    }

    /*
    @Override
    public void readFromNBT(NBTTagCompound nbt, String str) {
        str += name;
        currentState.state = (nbt.getDouble(str + "Istate"));
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt, String str) {
        str += name;
        nbt.setDouble(str + "Istate", currentState.state);
    }

     */

    public void resetStates() {
        currentState.setState(0);
    }
}
