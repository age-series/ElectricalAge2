package org.eln.sim.mna.process;

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln.sim.mna.component.ResistorSwitch;
import org.eln.sim.process.IProcess;

public class DiodeProcess implements IProcess {

    ResistorSwitch resistor;

    public DiodeProcess(ResistorSwitch resistor) {
        this.resistor = resistor;
    }

    @Override
    public void process(double time) {
        resistor.setState(resistor.getU() > 0);
    }
}
