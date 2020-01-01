package org.eln2.sim.process.destruct;

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

public interface IDestructable {
    void destructImpl();

    String describe();
}
