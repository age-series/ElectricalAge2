package org.eln2.sim.mna.component;

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

public class InterSystem extends Resistor {

    public static class InterSystemDestructor {
        boolean done = false;
    }

    @Override
    public boolean canBeReplacedByInterSystem() {
        return true;
    }
}
