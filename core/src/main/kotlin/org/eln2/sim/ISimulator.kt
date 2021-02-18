package org.eln2.sim

interface ISimulator {
    var slowPreProcess: MutableList<IProcess>
    var slowProcess: MutableList<IProcess>
    var slowPostProcess: MutableList<IProcess>
}
