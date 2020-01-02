package org.eln2.sim.mna.component;

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.sim.mna.SubSystem;
import org.eln2.sim.mna.misc.ISubSystemProcessI;
import org.eln2.sim.mna.state.CurrentState;
import org.eln2.sim.mna.state.State;

public class VoltageSource extends Bipole implements ISubSystemProcessI {//, INBTTReady {

    String name;

    double u = 0;
    private CurrentState currentState = new CurrentState();

    public VoltageSource(String name) {
        this.name = name;
    }

    public VoltageSource(String name, State aPin, State bPin) {
        super(aPin, bPin);
        this.name = name;
    }

    public VoltageSource setU(double u) {
        this.u = u;
        return this;
    }

    public double getU() {
        return u;
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
        if(s != null) {
            s.addState(getCurrentState());
            s.addProcess(this);
        }
    }

    @Override
    public void applyTo(SubSystem s) {
        s.addToA(getAPin(), getCurrentState(), 1.0);
        s.addToA(getBPin(), getCurrentState(), -1.0);
        s.addToA(getCurrentState(), getAPin(), 1.0);
        s.addToA(getCurrentState(), getBPin(), -1.0);
    }

    @Override
    public void simProcessI(SubSystem s) {
        s.addToI(getCurrentState(), u);
    }

    public double getI() {
        return -getCurrentState().state;
    }

    @Override
    public double getCurrent() {
        return -getCurrentState().state;
    }

    public CurrentState getCurrentState() {
        return currentState;
    }

    /*
    @Override
    public void readFromNBT(NBTTagCompound nbt, String str) {
        str += name;
        setU(nbt.getDouble(str + "U"));
        currentState.state = (nbt.getDouble(str + "Istate"));
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt, String str) {
        str += name;
        nbt.setDouble(str + "U", u);
        nbt.setDouble(str + "Istate", currentState.state);
    }*/

    public double getP() {
        return getU() * getI();
    }
}
