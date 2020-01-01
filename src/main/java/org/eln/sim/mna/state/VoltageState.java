package org.eln.sim.mna.state;

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

public class VoltageState extends State {

    public double getU() {
        return state;
    }

    public void setU(double state) {
        this.state = state;
    }
}
