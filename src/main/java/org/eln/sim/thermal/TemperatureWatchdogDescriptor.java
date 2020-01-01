package org.eln.sim.thermal;

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

public interface TemperatureWatchdogDescriptor {
    public double getUmax();

    public double getUmin();

    public double getBreakPropPerVoltOverflow();
}
