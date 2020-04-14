package org.eln2.sim.electrical.mna.component

import org.eln2.debug.dprintln
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

class IdealDiode : DynamicResistor() {
	override var name = "d"
	override val nodeCount = 2

	var minimumResistance = 1e-3
	var maximumResistance = 1e10

	override fun simStep() {
		// Theorem: changing the resistance should never lead to a change in sign of the current for a *SINGLE* timestep
		// as long as that is valid, this won't oscillate:
		dprintln("D.sS: in u=$potential r=$resistance")
		if (potential > 0) {
			if (resistance > minimumResistance) resistance = minimumResistance
		} else {
			if (resistance < maximumResistance) resistance = maximumResistance
		}
		dprintln("D.sS: out u=$potential r=$resistance")
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
			"falstad-old-led" to DiodeData("falstad-old-led", 2.2349907006671927e-18, 0.0, 2.0, 0.0),
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
	fun voltageScaleAt(temp: Double) = emissionCoef * thermalVoltage(temp)
	fun voltageDiodeCoefficientAt(temp: Double) = 1.0 / voltageScaleAt(temp)
	fun fwDropAt(temp: Double) = ln(1.0 / satCurrent + 1.0) * voltageScaleAt(temp)
	fun voltageCriticalAt(temp: Double): Double {
		val voltageThermalScaled = voltageScaleAt(temp)
		return voltageThermalScaled * ln(voltageThermalScaled / (sqrt2 * satCurrent))
	}

	fun voltageCriticalZenerAt(temp: Double): Double {
		val voltageThermal = thermalVoltage(temp)
		return voltageThermal * ln(voltageThermal / (sqrt2 * satCurrent))
	}

	// The current is expressed as a _negative_ current
	fun zenerOffsetAt(temp: Double, current: Double = -5e-3) = if (!isZener) 0.0 else breakdownVoltage - ln(-(1.0 + current / satCurrent)) * thermalVoltage(temp)

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

class RealisticDiode(var model: DiodeData) : DynamicResistor() {
	var temp = 300.0

	var lastPotential = 0.0

	override var current: Double = 0.0
		set(value) {
			if (isInCircuit)
				circuit!!.stampCurrentSource(pos.index, neg.index, value - field)
			field = value
		}

	override fun simStep() {
		println("RD.sS: u=$potential lastU=$lastPotential")
		if (abs(potential - lastPotential) < circuit!!.slack) return

		val newPotential = model.solveIter(temp, potential, lastPotential)
		lastPotential = potential
		println("RD.sS: newU=$newPotential")

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

		println("RD.sS: geq=$conductance r=${1 / conductance} i=$newCurrent")

		resistance = 1 / conductance
		current = newCurrent
	}
}
