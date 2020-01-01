package org.eln.sim.thermal.process;

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln.sim.process.IProcess;
import org.eln.sim.thermal.ThermalLoad;
import org.eln.sim.mna.component.Resistor;

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
