package org.eln.sim.thermal;

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

public class ThermalConnection {

    public ThermalLoad L1;
    public ThermalLoad L2;

    public ThermalConnection(ThermalLoad L1, ThermalLoad L2) {
        this.L1 = L1;
        this.L2 = L2;
    }
}
