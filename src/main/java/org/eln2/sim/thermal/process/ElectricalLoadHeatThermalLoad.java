package org.eln2.sim.thermal.process;

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.sim.mna.state.ElectricalLoad;
import org.eln2.sim.process.IProcess;
import org.eln2.sim.thermal.ThermalLoad;

public class ElectricalLoadHeatThermalLoad implements IProcess {

    ElectricalLoad r;
    ThermalLoad load;

    public ElectricalLoadHeatThermalLoad(ElectricalLoad r, ThermalLoad load) {
        this.r = r;
        this.load = load;
    }

    @Override
    public void process(double time) {
        if (r.isNotSimulated()) return;
        double I = r.getI();
        load.movePowerTo(I * I * r.getRs() * 2);
    }

	/*double powerMax = 100000;
    public void setDeltaTPerSecondMax(double deltaTPerSecondMax) {
		powerMax = deltaTPerSecondMax*load.C;
	}*/
}
