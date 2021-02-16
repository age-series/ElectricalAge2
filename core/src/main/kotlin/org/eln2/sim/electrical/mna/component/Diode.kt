package org.eln2.sim.electrical.mna.component

import org.eln2.debug.dprintln
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * An Ideal Diode.
 *
 * This Diode has perfect behavior; when positively biased, it acts like a resistor of [minimumResistance], while it acts like a resistor of [maximumResistance] when negatively biased. Thus, it is described as a piecewise function of two lines on the V-I chart, with the discontinuity at 0. This is unphysical behavior, but very fast to compute; it will converge in at most one extra substep.
 *
 * Note that the stability of the matrix solver is approximately a function of the differences of the order of magnitude of [maximumResistance] and [minimumResistance]; less orders of magnitude difference will improve stability at the cost of behaving more and more degenerately like a resistor.
 */
class IdealDiode : Resistor() {
    override var name = "d"

    /**
     * The resistance this diode should have when forward-biased; ideally zero, but this will cause the MNA matrix to be ill-formed.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    var minimumResistance = 1e-3

    /**
     * The resistance this diode should have when reverse-biased; may be infinite, but this can cause component float, so it is just a large value by default.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    var maximumResistance = 1e10

    override fun simStep() {
        // Theorem: changing the resistance should never lead to a change in sign of the current for a *SINGLE* timestep
        // as long as that is valid, this won't oscillate:
        dprintln("in u=$potential r=$resistance")
        if (potential > 0) {
            if (resistance > minimumResistance) resistance = minimumResistance
        } else {
            if (resistance < maximumResistance) resistance = maximumResistance
        }
        dprintln("out u=$potential r=$resistance")
    }

    override fun detail(): String {
        val bias = if (resistance == minimumResistance) {
            "Forward Bias Mode (conducting)"
        }else{
            "Reverse Bias Mode (blocking)"
        }
        return "[ideal diode $name: ${potential}v, ${current}A, ${resistance}Ω, ${power}W, $bias]"
    }
}

/**
 * Diode data of simulations of diodes, imported from Falstad.
 *
 * Some of the methods and data here are horribly explained, for which I'm sorry--I'm not an expert on diode physics.
 */
data class DiodeData(
    /**
     * The name of this model of diode.
     */
    val name: String,
    /**
     * The saturation current, dividing the critical voltage.
     */
    val satCurrent: Double,
    /**
     * The serial resistance of the diode; can be 0, since it will always have a positive contribution.
     */
    val resistance: Double,
    /**
     * The emission coefficient of the diode, relating ideal performance with the thermal voltage of the electrons.
     */
    val emissionCoef: Double,
    /**
     * For Zener diodes, the breakdown voltage for reverse-bias.
     */
    val breakdownVoltage: Double
) {
    companion object {
        /**
         * A map of [name] to [DiodeData], naming the diodes known to Falstad.
         */
        val diodes = mapOf(
            "spice-default" to DiodeData("spice-default", 1e-14, 0.0, 1.0, 0.0),
            "falstad-default" to DiodeData("falstad-default", 1.7143528192808883e-7, 0.0, 2.0, 0.0),
            "falstad-zener" to DiodeData("falstad-zener", 1.7143528192808883e-7, 0.0, 2.0, 5.6),
            "falstad-old-led" to DiodeData("falstad-old-led", 2.2349907006671927e-18, 0.0, 2.0, 0.0),
            "falstad-led" to DiodeData("falstad-led", 93.2e-12, 0.042, 3.73, 0.0),
            "schottky-1N5711" to DiodeData("schottky-1N5711", 315e-9, 2.8, 2.03, 70.0),
            "schottky-1N5712" to DiodeData("schottky-1N5712", 680e-12, 12.0, 1.003, 20.0),
            "germanium-1N34" to DiodeData("germanium-1N34", 200e-12, 84e-3, 2.19, 60.0),
            "1N4004" to DiodeData("1N4004", 18.8e-9, 28.6e-3, 2.0, 400.0),
            "1N3891" to DiodeData("1N3891", 63e-9, 9.6e-3, 2.0, 0.0),
            "switching-1N4148" to DiodeData("switching-1N4148", 4.35e-9, 0.6458, 1.0, 75.0)
        )

        /**
         * An opinionated default diode model.
         */
        val default: DiodeData = diodes["falstad-default"] ?: error("no default diode!")

        /**
         * Boltzmann's constant, relating energy and temperature.
         */
        @Suppress("MemberVisibilityCanBePrivate")
        const val boltzmann = 1.380649e-23 // J/K

        /**
         * The elementary charge of one electron in Coulombs.
         */
        @Suppress("MemberVisibilityCanBePrivate")
        const val elemcharge = 1.602176634e-19 // Q

        /**
         * The square root of two.
         */
        val sqrt2 = sqrt(2.0)

        // temp is in K
        /**
         * Get the thermal voltage at the given [temp] in Kelvin.
         *
         * At STP (297.15K), this is about 25.6mV.
         *
         * This parameter generally scales all other diode parameters, including the Zener band gap (below).
         */
        fun thermalVoltage(temp: Double) = temp * boltzmann / elemcharge

        /**
         * Get the Zener coefficient at the given [temp] in Kelvin.
         *
         * This is the reciprocal of the thermal voltage.
         */
        fun zenerCoefficient(temp: Double) = 1.0 / thermalVoltage(temp)
    }

    /**
     * Determine if this is a Zener-model diode (having nonzero breakdown voltage).
     */
    @Suppress("unused")
    val isZener: Boolean get() = breakdownVoltage != 0.0

    /**
     * Get the "voltage scale" (the emission coefficient times the thermal voltage) at the given [temp] in Kelvin.
     */
    private fun voltageScaleAt(temp: Double) = emissionCoef * thermalVoltage(temp)

    /**
     * Get the "diode coefficient" (reciprocal of the "voltage scale") at the given [temp] in Kelvin.
     */
    fun voltageDiodeCoefficientAt(temp: Double) = 1.0 / voltageScaleAt(temp)

    /**
     * Get the forward voltage drop at the given [temp] in Kelvin.
     */
    @Suppress("unused")
    fun fwDropAt(temp: Double) = ln(1.0 / satCurrent + 1.0) * voltageScaleAt(temp)

    /**
     * Get the "critical voltage" for the [temp] in Kelvin.
     */
    private fun voltageCriticalAt(temp: Double): Double {
        val voltageThermalScaled = voltageScaleAt(temp)
        return voltageThermalScaled * ln(voltageThermalScaled / (sqrt2 * satCurrent))
    }

    /**
     * Get the Zener "critical voltage" for the [temp] in Kelvin.
     *
     * Despite being a reverse bias, this returns a positive value.
     */
    private fun voltageCriticalZenerAt(temp: Double): Double {
        val voltageThermal = thermalVoltage(temp)
        return voltageThermal * ln(voltageThermal / (sqrt2 * satCurrent))
    }

    // The current is expressed as a _negative_ current
    /**
     * Get the "Zener offset" (approximate knee point) of the Zener breakdown curve for the given [current] in Amperes and [temp] in Kelvin.
     *
     * [current] is expected to be negative due to the reverse bias, and is (negative) 5mA by default.
     */
    fun zenerOffsetAt(temp: Double, current: Double = -5e-3) =
        if (!isZener) 0.0 else breakdownVoltage - ln(-(1.0 + current / satCurrent)) * thermalVoltage(temp)

    /**
     * Do a "solve" iteration; given the previous potential [vold] and the current potential [vnew], compute a desired new potential (slipping down the V-I curve) at [temp] Kelvin. See [RealisticDiode.simStep] for use.
     */
    fun solveIter(temp: Double, vnew: Double, vold: Double): Double {
        dprintln("DD.sI: temp=$temp voltageNew=$vnew voltageOld=$vold")
        var voltageNew = vnew
        var voltageOld = vold
        val voltageThermal = thermalVoltage(temp)
        val voltageThermalScaled = voltageScaleAt(temp)
        val voltageCritical = voltageCriticalAt(temp)

        if (voltageNew < 0 && isZener) {
            val zenerOffset = zenerOffsetAt(temp)
            val voltageCriticalZener = voltageCriticalZenerAt(temp)
            dprintln("DD.sI: zenerOffset=$zenerOffset voltageCriticalZener=$voltageCriticalZener")
            voltageNew = -voltageNew - zenerOffset
            voltageOld = -voltageOld - zenerOffset

            if (voltageNew > voltageCriticalZener && abs(voltageNew - voltageOld) > 2.0 * voltageThermal) {
                val tmp = 1.0 + (voltageNew - voltageOld) / voltageThermal
                voltageNew =
                    if (voltageOld > 0 && tmp > 0) {
                        voltageOld + voltageThermal * ln(tmp)
                    } else if (voltageOld > 0) {
                        voltageCriticalZener
                    } else {
                        voltageThermal * ln(voltageNew / voltageThermal)
                    }
            }

            voltageNew = -(voltageNew + zenerOffset)
        } else {
            if (voltageNew > voltageCritical && abs(voltageNew - voltageOld) > 2.0 * voltageThermalScaled) {
                val tmp = 1.0 + (voltageNew - voltageOld) / voltageThermalScaled
                voltageNew =
                    if (voltageOld > 0 && tmp > 0) {
                        voltageOld + voltageThermalScaled * ln(tmp)
                    } else if (voltageOld > 0) {
                        voltageCritical
                    } else {
                        voltageThermalScaled * ln(voltageNew / voltageThermalScaled)
                    }
            }
        }

        dprintln("DD.sI: voltageOut=$voltageNew")
        return voltageNew
    }
}

/**
 * A "realistic" diode, with model parameters carefully tuned to match real-world diode effects (at least in the physically ideal sense, ceteris parebus). This does not, for example, simulate the parasitic inductive/capacitive effects that all physical poles have, but it approximates diodes to the best of their manufacturer's specifications.
 *
 * Most of the logic is in the [DiodeData] passed as the [model]. You can construct your own data, or use any of the [DiodeData.diodes] already predefined; [DiodeData.default] is an opinionated choice of a default diode.
 *
 * Real diodes have two exponential curves on their V-I plot, for forward and reverse bias, with a fairly flat slope in the reverse-bias quadrant (III, and slightly into I due to saturation) being the main motivation for their use. At sufficient reverse bias, the diode "breaks down" and becomes ohmic again; all diodes do this (though some may be well outside of safe specification to do so), but diodes especially designed for this are called Zener diodes, and have a carefully-regulated reverse-bias breakdown so as to achieve a very accurate reverse voltage drop.
 *
 * This is certainly not as computationally efficient as the [IdealDiode], but it is a vastly superior model. The cost is that it may take several substeps to converge (as a non-linear component), but the convergence should be exponential nonetheless.
 */
class RealisticDiode(private var model: DiodeData) : Resistor() {
    private var temp = 300.0

    private var lastPotential = 0.0

    override var current: Double = 0.0
        set(value) {
            if (isInCircuit && pos != null && neg != null)
                circuit!!.stampCurrentSource(pos!!.index, neg!!.index, value - field)
            field = value
        }

    override fun simStep() {
        dprintln("u=$potential lastU=$lastPotential")
        if (abs(potential - lastPotential) < circuit!!.slack) return

        val newPotential = model.solveIter(temp, potential, lastPotential)
        lastPotential = potential
        dprintln("newU=$newPotential")

        val voltageDiodeCoefficient = model.voltageDiodeCoefficientAt(temp)
        val ex = exp(newPotential * voltageDiodeCoefficient)
        val conductance =
            model.satCurrent * 0.01 + if (newPotential >= 0.0 || !model.isZener) {
                voltageDiodeCoefficient * model.satCurrent * ex
            } else {
                val zenerCoefficient = DiodeData.zenerCoefficient(temp)
                voltageDiodeCoefficient * ex + zenerCoefficient * exp((-newPotential - model.zenerOffsetAt(temp)) * zenerCoefficient)
            }
        val newCurrent =
            if (newPotential >= 0.0 || !model.isZener) {
                (ex - 1.0) * model.satCurrent - conductance * newPotential
            } else {
                (ex - exp((-newPotential - model.zenerOffsetAt(temp)) * DiodeData.zenerCoefficient(temp)) - 1.0) * model.satCurrent + conductance * (-newPotential)
            }

        dprintln("geq=$conductance r=${1 / conductance} i=$newCurrent")

        resistance = 1 / conductance
        current = newCurrent
    }

    override fun detail(): String {
        return "[realistic diode (${model.name}) $name: ${potential}v, ${current}A, ${resistance}Ω, ${power}W]"
    }
}
