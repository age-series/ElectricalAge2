package org.eln.sim.process.destruct;

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln.sim.mna.component.Bipole;

public class BipoleVoltageWatchdog extends ValueWatchdog {

    Bipole bipole;

    public BipoleVoltageWatchdog set(Bipole bipole) {
        this.bipole = bipole;
        return this;
    }

    public BipoleVoltageWatchdog setUNominal(double UNominal) {
        this.max = UNominal * 1.3;
        this.min = -max;
        this.timeoutReset = UNominal * 0.10 * 5;

        return this;
    }

    @Override
    double getValue() {
        return bipole.getU();
    }
}
