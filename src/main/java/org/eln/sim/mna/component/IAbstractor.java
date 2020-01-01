package org.eln.sim.mna.component;

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln.sim.mna.SubSystem;

public interface IAbstractor {

    void dirty(Component component);

    SubSystem getAbstractorSubSystem();
}
