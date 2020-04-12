package org.eln2.sim.electrical.mna.component

import org.eln2.debug.dprintln
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

class IdealDiode: DynamicResistor() {
    override var name = "d"
    override val nodeCount = 2
    
    var minR = 1e-3
    var maxR = 1e10

    override fun simStep() {
        // Theorem: changing the resistance should never lead to a change in sign of the current for a *SINGLE* timestep
        // as long as that is valid, this won't oscillate:
        dprintln("D.sS: in u=$u r=$r")
        if(u > 0) {
            if(r > minR) r = minR
        } else {
            if(r < maxR) r = maxR
        }
        dprintln("D.sS: out u=$u r=$r")
    }
}

data class DiodeData(
        val name: String,
        val satCurrent: Double,
        val resistance: Double,
        val emissionCoef: Double,
        val breakdownVoltage: Double
) {
    companion object {
        val diodes = mapOf(
                "spice-default" to DiodeData("spice-default", 1e-14, 0.0, 1.0, 0.0),
                "falstad-default" to DiodeData("falstad-default", 1.7143528192808883e-7, 0.0, 2.0, 0.0),
                "falstad-zener" to DiodeData("falstad-zener", 1.7143528192808883e-7, 0.0, 2.0, 5.6),
                "falstad-old-led" to DiodeData("falstad-old-led",2.2349907006671927e-18, 0.0, 2.0, 0.0),
                "falstad-led" to DiodeData("falstad-led", 93.2e-12, 0.042, 3.73, 0.0),
                "schottky-1N5711" to DiodeData("schottky-1N5711", 315e-9, 2.8, 2.03, 70.0),
                "schottky-1N5712" to DiodeData("schottky-1N5712", 680e-12, 12.0, 1.003, 20.0),
                "germanium-1N34" to DiodeData("germanium-1N34", 200e-12, 84e-3, 2.19, 60.0),
                "1N4004" to DiodeData("1N4004", 18.8e-9, 28.6e-3, 2.0, 400.0),
                "1N3891" to DiodeData("1N3891", 63e-9, 9.6e-3, 2.0, 0.0),
                "switching-1N4148" to DiodeData("switching-1N4148", 4.35e-9, 0.6458, 1.0, 75.0)
        )
        
        val default: DiodeData = diodes["falstad-default"] ?: error("no default diode!")
        
        const val boltzmann = 1.380649e-23  // J/K
        const val elemcharge = 1.602176634e-19  // Q
        val sqrt2 = sqrt(2.0)
        // temp is in K
        inline fun thermalVoltage(temp: Double) = temp * boltzmann / elemcharge
        inline fun zenerCoefficient(temp: Double) = 1.0 / thermalVoltage(temp)
    }

    val isZener: Boolean get() = breakdownVoltage != 0.0
    fun vScaleAt(temp: Double) = emissionCoef * thermalVoltage(temp)
    fun vdCoefAt(temp: Double) = 1.0 / vScaleAt(temp)
    fun fwDropAt(temp: Double) = ln(1.0 / satCurrent + 1.0) * vScaleAt(temp)
    fun vCritAt(temp: Double): Double {
        val vs = vScaleAt(temp)
        return vs * ln(vs / (sqrt2 * satCurrent))
    }
    fun vZCritAt(temp: Double): Double {
        val vt = thermalVoltage(temp)
        return vt * ln(vt / (sqrt2 * satCurrent))
    }
    // The current is expressed as a _negative_ current
    fun vZOffsetAt(temp: Double, current: Double = -5e-3) = if(!isZener) 0.0 else breakdownVoltage - ln(-(1.0 + current/satCurrent)) * thermalVoltage(temp)

    fun solveIter(temp: Double, vnew: Double, vold: Double): Double {
        dprintln("DD.sI: temp=$temp vnew=$vnew vold=$vold")
        var vnew = vnew
        var vold = vold
        val vt = thermalVoltage(temp)
        val vsc = vScaleAt(temp)
        val vcr = vCritAt(temp)
        if(vnew > vcr && abs(vnew - vold) > 2.0 * vsc) {
            vnew = if(vold > 0) {
                val tmp = 1.0 + (vnew - vold) / vsc
                if(tmp > 0) {
                    vold + vsc * ln(tmp)
                } else {
                    vcr
                }
            } else {
                vsc * ln(vnew / vsc)
            }
        } else if(vnew < 0 && isZener) {
            val zoff = vZOffsetAt(temp)
            val vzc = vZCritAt(temp)
            dprintln("DD.sI: zoff=$zoff vzc=$vzc")
            vnew = -vnew - zoff
            vold = -vold - zoff

            if(vnew > vzc && abs(vnew - vold) > 2.0 * vt) {
                vnew = if(vold > 0) {
                    val tmp = 1.0 + (vnew - vold) / vt
                    if(tmp > 0) {
                        vold + vt * ln(tmp)
                    } else {
                        vzc
                    }
                } else {
                    vt * ln(vnew / vt)
                }
            }

            vnew = -(vnew + zoff)
        }

        dprintln("DD.sI: out=$vnew")
        return vnew
    }
}

class RealisticDiode(var model: DiodeData): DynamicResistor() {
    var temp = 300.0
    
    var lastU = 0.0
    
    override var i: Double = 0.0
        set(value) {
            if(isInCircuit)
                circuit!!.stampCurrentSource(pos.index, neg.index, value - field)
            field = value
        }

    override fun simStep() {
        println("RD.sS: u=$u lastU=$lastU")
        if(abs(u - lastU) < circuit!!.slack) return

        val newU = model.solveIter(temp, u, lastU)
        lastU = u
        println("RD.sS: newU=$newU")

        val vdc = model.vdCoefAt(temp)
        val ex = exp(newU * vdc)
        val geq = model.satCurrent * 0.01 + if(newU >= 0.0 || !model.isZener) {
            vdc * model.satCurrent * ex
        } else {
            val vzc = DiodeData.zenerCoefficient(temp)
            vdc * ex + vzc * exp((-newU - model.vZOffsetAt(temp)) * vzc)
        }
        val nc = if(newU >= 0.0 || !model.isZener) {
            (ex - 1.0) * model.satCurrent - geq * newU
        } else {
            (ex - exp((-newU - model.vZOffsetAt(temp)) * DiodeData.zenerCoefficient(temp)) - 1.0) * model.satCurrent + geq * (-newU)
        }

        println("RD.sS: geq=$geq r=${1/geq} i=$nc")

        r = 1 / geq
        i = nc
    }
}