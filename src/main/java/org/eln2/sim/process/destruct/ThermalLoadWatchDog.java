package org.eln2.sim.process.destruct;

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.sim.thermal.ThermalLoad;

public class ThermalLoadWatchDog extends ValueWatchdog {

    ThermalLoad state;

    @Override
    double getValue() {
        return state.getT();
    }

    public ThermalLoadWatchDog set(ThermalLoad state) {
        this.state = state;
        return this;
    }

    public ThermalLoadWatchDog setTMax(double tMax) {
        this.max = tMax;
        this.min = -40;
        this.timeoutReset = tMax * 0.1 * 10;
        return this;
    }

    /*
    public ThermalLoadWatchDog set(ThermalLoadInitializer t) {
        this.max = t.warmLimit;
        this.min = t.coolLimit;
        this.timeoutReset = max * 0.1 * 10;
        return this;
    }*/

    public ThermalLoadWatchDog setLimit(double thermalWarmLimit, double thermalCoolLimit) {
        this.max = thermalWarmLimit;
        this.min = thermalCoolLimit;
        this.timeoutReset = max * 0.1 * 10;
        return this;
    }
}
