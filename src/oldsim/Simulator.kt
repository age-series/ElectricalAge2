package org.eln2.oldsim

import org.eln2.oldsim.electrical.RootSystem
import org.eln2.sim.IProcess
import org.eln2.oldsim.thermal.ThermalConnection
import org.eln2.oldsim.thermal.ThermalLoad

class Simulator (var callPeriod: Double, var electricalPeriod: Double, var electricalInterSystemOverSampling: Int, var thermalPeriod: Double) {

    var mna: RootSystem

    var slowProcessList = mutableSetOf<IProcess>()
    var slowPreProcessList = mutableSetOf<IProcess>()
    var slowPostProcessList = mutableSetOf<IProcess>()

    var electricalProcessList = mutableSetOf<IProcess>()

    var thermalFastProcessList = mutableSetOf<IProcess>()
    var thermalSlowProcessList = mutableSetOf<IProcess>()

    val thermalFastConnectionList = mutableSetOf<ThermalConnection>()
    val thermalFastLoadList = mutableSetOf<ThermalLoad>()
    val thermalSlowConnectionList = mutableSetOf<ThermalConnection>()
    val thermalSlowLoadList = mutableSetOf<ThermalLoad>()

    var run: Boolean? = null

    var nodeCount: Int = 0

    var averageTickTime: Double = 0.0

    var slowNsStack: Long = 0
    var electricalNsStack: Long = 0
    var thermalFastNsStack: Long = 0
    var thermalSlowNsStack: Long = 0

    var timeout: Double = 0.0

    var electricalTimeout: Double = 0.0
    var thermalTimeout: Double = 0.0

    private var printTimeCounter: Int = 0

    init {
        mna = RootSystem(electricalPeriod, electricalInterSystemOverSampling)
        run = false
    }

    fun init() {
        nodeCount = 0
        mna = RootSystem(electricalPeriod, electricalInterSystemOverSampling)
        slowProcessList.clear()
        slowPreProcessList.clear()
        slowPostProcessList.clear()
        electricalProcessList.clear()
        thermalFastProcessList.clear()
        thermalSlowProcessList.clear()
        thermalFastConnectionList.clear()
        thermalFastLoadList.clear()
        thermalSlowConnectionList.clear()
        thermalSlowLoadList.clear()
        run = true
    }

    fun stop() {
        nodeCount = 0
        slowProcessList.clear()
        slowPreProcessList.clear()
        slowPostProcessList.clear()
        electricalProcessList.clear()
        thermalFastProcessList.clear()
        thermalSlowProcessList.clear()
        thermalFastConnectionList.clear()
        thermalFastLoadList.clear()
        thermalSlowConnectionList.clear()
        thermalSlowLoadList.clear()
        run = false
    }

    fun tick() {
        var stackStart: Long
        val startTime = System.nanoTime()
        for (o in slowPreProcessList.toTypedArray()) {
            o.process(1.0 / 20)
        }
        timeout += callPeriod
        while (timeout > 0) {
            if (timeout < electricalTimeout && timeout < thermalTimeout) {
                thermalTimeout -= timeout
                electricalTimeout -= timeout
                timeout = 0.0
                break
            }
            var dt: Double
            if (electricalTimeout <= thermalTimeout) {
                dt = electricalTimeout
                electricalTimeout += electricalPeriod
                stackStart = System.nanoTime()
                mna.step()
                electricalProcessList.forEach { it.process(electricalPeriod) }
                electricalNsStack += System.nanoTime() - stackStart
            } else {
                dt = thermalTimeout
                thermalTimeout += thermalPeriod
                stackStart = System.nanoTime()
                thermalStep(thermalPeriod, thermalFastConnectionList, thermalFastProcessList, thermalFastLoadList)
                thermalFastNsStack += System.nanoTime() - stackStart
            }
            thermalTimeout -= dt
            electricalTimeout -= dt
            timeout -= dt
        }
        run {
            stackStart = System.nanoTime()
            thermalStep(0.05, thermalSlowConnectionList, thermalSlowProcessList, thermalSlowLoadList)
            thermalSlowNsStack += System.nanoTime() - stackStart
        }
        stackStart = System.nanoTime()
        slowProcessList.forEach { it.process(0.05) }
        slowNsStack += System.nanoTime() - stackStart
        averageTickTime += 1.0 / 20 * ((System.nanoTime() - startTime).toInt() / 1000.0)

        if (++printTimeCounter == 20) {
            printTimeCounter = 0
            electricalNsStack /= 20
            thermalFastNsStack /= 20
            thermalSlowNsStack /= 20
            slowNsStack /= 20

            println("ticks ${averageTickTime}us " +
                    "E ${electricalNsStack/1000} " +
                    "TF ${thermalFastNsStack/1000} " +
                    "TS ${thermalSlowNsStack/1000} " +
                    "S ${slowNsStack/1000} " +
                    "SS ${mna.getSubSystemCount()} " +
                    "EP ${electricalProcessList.size} " +
                    "TFL ${thermalFastLoadList.size} " +
                    "TFC ${thermalFastConnectionList.size} " +
                    "TFP ${thermalFastProcessList.size} " +
                    "TSL ${thermalSlowLoadList.size} " +
                    "TSP ${thermalSlowProcessList.size} " +
                    "SP ${slowProcessList.size}")

            averageTickTime = 0.0
            electricalNsStack = 0
            thermalFastNsStack = 0
            slowNsStack = 0
            thermalSlowNsStack = 0
        }
        for (o in slowPostProcessList) {
            o.process(1 / 20.0)
        }
    }

    // TODO: Probably move elsewhere?
    fun thermalStep(dt: Double, connectionList: Iterable<ThermalConnection>, processList: Iterable<IProcess>?, loadList: Iterable<ThermalLoad>) {
        for (c in connectionList) {
            var i: Double
            i = (c.l2.getT() - c.l1.Tc) / (c.l2.Rs + c.l1.Rs)
            c.l1.PcTemp += i
            c.l2.PcTemp -= i
            c.l1.PrsTemp += Math.abs(i)
            c.l2.PrsTemp += Math.abs(i)
        }
        if (processList != null) {
            for (process in processList) {
                process.process(dt)
            }
        }
        for (load in loadList) {
            load.PcTemp = load.PcTemp - load.Tc / load.C
            load.Tc += load.PcTemp * dt / load.C
            load.Pc = load.PcTemp
            load.Prs = load.PrsTemp
            load.Psp = load.PspTemp
            load.PcTemp = 0.0
            load.PrsTemp = 0.0
            load.PspTemp = 0.0
        }
    }

    // TODO: Move out out here!
    fun getMinimalThermalC(Rs: Double, Rp: Double): Double {
        return thermalPeriod * 3 / (1 / (1 / Rp + 1 / Rs))
    }

    // TODO: Move out of here!
    fun checkThermalLoad(thermalRs: Double, thermalRp: Double, thermalC: Double): Boolean {
        if (thermalC < getMinimalThermalC(thermalRs, thermalRp)) {
            println("checkThermalLoad ERROR")
            return false
        }
        return true
    }
}