package org.eln.sim.process.destruct

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

import org.eln.sim.process.IProcess
import org.eln.sim.Simulator

class DelayedDestruction(val dest: IDestructable, var tmout: Double): IProcess {
    init {
        Simulator.getInstance().addSlowProcess(this)
    }

    override fun process(time: Double) {
        tmout -= time
        if(tmout <= 0.0) {
            dest.destructImpl()
            Simulator.getInstance().removeSlowProcess(this)
        }
    }
}
