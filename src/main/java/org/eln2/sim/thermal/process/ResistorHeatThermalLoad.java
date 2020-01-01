package org.eln2.sim.thermal.process;

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.sim.process.IProcess;
import org.eln2.sim.thermal.ThermalLoad;
import org.eln2.sim.mna.component.Resistor;

public class ResistorHeatThermalLoad implements IProcess {

    Resistor r;
    ThermalLoad load;

    public ResistorHeatThermalLoad(Resistor r, ThermalLoad load) {
        this.r = r;
        this.load = load;
    }

    @Override
    public void process(double time) {
        load.movePowerTo(r.getP());
    }
}
