package org.eln2.sim.process.destruct;

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln2.sim.process.IProcess;

public abstract class ValueWatchdog implements IProcess {

    IDestructable destructable;
    double perOverflowStrenght = 1;
    double min;
    double max;

    double timeoutReset = 2;

    double timeout = 0;
    boolean boot = true;
    boolean joker = true;

    double rand = Math.random() + 0.5;

    @Override
    public void process(double time) {
        if (boot) {
            boot = false;
            timeout = timeoutReset;
        }
        double value = getValue();
        double overflow = Math.max(value - max, min - value);
        if (overflow > 0) {
            if (joker) {
                joker = false;
                overflow = 0;
            }
        } else {
            joker = true;
        }

        timeout -= time * overflow * rand;
        if (timeout > timeoutReset) {
            timeout = timeoutReset;
        }
        if (timeout < 0) {
            System.out.println("destroying thing");
            destructable.destructImpl();
        }
    }

    public ValueWatchdog set(IDestructable d) {
        this.destructable = d;
        return this;
    }

    abstract double getValue();

    public void disable() {
        this.max = 100000000;
        this.min = -max;
        this.timeoutReset = 10000000;
    }

    public void reset() {
        boot = true;
    }
}
