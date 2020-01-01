package org.eln2.sim.mna.state;

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

public class VoltageStateLineReady extends VoltageState {

    boolean canBeSimplifiedByLine = false;

    public void setCanBeSimplifiedByLine(boolean v) {
        this.canBeSimplifiedByLine = v;
    }

    @Override
    public boolean canBeSimplifiedByLine() {
        return canBeSimplifiedByLine;
    }
}
