package org.eln2.sim.thermal

/*
NOTE: DO NOT IMPORT MINECRAFT CODE IN THIS CLASS
EXTEND IT INSTEAD IN THE org.eln.nbt DIRECTORY
 */

open class ThermalLoad {
    open var Tc = 0.0
    open var Rp = 0.0
    open var Rs = 0.0
    open var C = 0.0
    open var PcTemp = 0.0
    open var Pc = 0.0
    open var Prs = 0.0
    open var Psp = 0.0
    open var PrsTemp = 0.0
    open var PspTemp = 0.0

    open var isSlow = false

    constructor() {
        setHighImpedance()
        Tc = 0.0
        PcTemp = 0.0
        Pc = 0.0
        Prs = 0.0
        Psp = 0.0
    }

    constructor(Tc: Double, Rp: Double, Rs: Double, C: Double) {
        this.Tc = Tc
        this.Rp = Rp
        this.Rs = Rs
        this.C = C
        PcTemp = 0.0
    }

    open fun setRsByTao(tao: Double) {
        Rs = tao / C
    }

    open fun setHighImpedance() {
        Rs = 1000000000.0
        C = 1.0
        Rp = 1000000000.0
    }

    open val externalLoad = ThermalLoad(0.0, 0.0, 0.0, 0.0)

    open fun getPower(): Double {
        return if (java.lang.Double.isNaN(Prs) || java.lang.Double.isNaN(Pc) || java.lang.Double.isNaN(Tc) || java.lang.Double.isNaN(Rp) || java.lang.Double.isNaN(Psp)) 0.0 else (Prs + Math.abs(Pc) + Tc / Rp + Psp) / 2
    }

    open operator fun set(Rs: Double, Rp: Double, C: Double) {
        this.Rp = Rp
        this.Rs = Rs
        this.C = C
    }

    open fun moveEnergy(energy: Double, time: Double, from: ThermalLoad, to: ThermalLoad) {
        if (!energy.isFinite() || time == 0.0 || time == -0.0 || !from.PcTemp.isFinite() || !from.PspTemp.isFinite()) return
        val I = energy / time
        val absI = Math.abs(I)
        from.PcTemp -= I
        to.PcTemp += I
        from.PspTemp += absI
        to.PspTemp += absI
    }

    open fun movePower(power: Double, from: ThermalLoad, to: ThermalLoad) {
        if (!power.isFinite() || !from.PcTemp.isFinite() || !from.PspTemp.isFinite()) return
        val absI = Math.abs(power)
        from.PcTemp -= power
        to.PcTemp += power
        from.PspTemp += absI
        to.PspTemp += absI
    }

    open fun movePowerTo(power: Double) {
        if (!power.isFinite()) return
        val absI = Math.abs(power)
        PcTemp += power
        PspTemp += absI
    }

    open fun getT(): Double {
        if (java.lang.Double.isNaN(Tc)) {
            Tc = 0.0
        }
        return Tc
    }

    open fun setAsSlow() {
        isSlow = true
    }

    open fun setAsFast() {
        isSlow = false
    }
}