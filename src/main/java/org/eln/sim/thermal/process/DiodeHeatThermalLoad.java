package org.eln.sim.thermal.process;

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln.sim.process.IProcess;
import org.eln.sim.thermal.ThermalLoad;
import org.eln.sim.mna.component.Resistor;

public class DiodeHeatThermalLoad implements IProcess {

    Resistor r;
    ThermalLoad load;
    double lastR;

    public DiodeHeatThermalLoad(Resistor r, ThermalLoad load) {
        this.r = r;
        this.load = load;
        lastR = r.getR();
    }

    @Override
    public void process(double time) {
        if (r.getR() == lastR) {
            load.movePowerTo(r.getP());
        } else {
            lastR = r.getR();
        }
    }
}
