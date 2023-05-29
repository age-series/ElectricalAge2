package org.eln2.mc.extensions

import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Component
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.ageseries.libage.sim.electrical.mna.component.VoltageSource
import org.ageseries.libage.sim.thermal.ConnectionParameters
import org.ageseries.libage.sim.thermal.Simulator
import org.ageseries.libage.sim.thermal.ThermalMass
import org.eln2.mc.common.cells.foundation.ComponentHolder
import org.eln2.mc.common.cells.foundation.ElectricalComponentInfo
import org.eln2.mc.sim.EnvironmentInformation
import org.eln2.mc.sim.ThermalBody
import kotlin.math.abs

private const val EPSILON = 0.001

fun Component.connect(pin: Int, info: ElectricalComponentInfo){
    this.connect(pin, info.component, info.index)
}

fun Circuit.add(holder: ComponentHolder<*>){
    this.add(holder.instance)
}

fun Resistor.setResistanceEpsilon(resistance: Double, epsilon: Double = EPSILON): Boolean {
    if(abs(this.resistance - resistance) < epsilon){
        return false
    }

    this.resistance = resistance

    return true
}

fun VoltageSource.setPotentialEpsilon(potential: Double, epsilon: Double = EPSILON): Boolean {
    if(abs(this.potential - potential) < epsilon){
        return false
    }

    this.potential = potential

    return true
}

fun Simulator.add(body: ThermalBody) {
    this.add(body.thermal)
}

fun Simulator.remove(body: ThermalBody) {
    this.remove(body.thermal)
}

fun Simulator.connect(a: ThermalBody, b: ThermalBody, parameters: ConnectionParameters){
    this.connect(a.thermal, b.thermal, parameters)
}

fun Simulator.connect(a: ThermalMass, environmentInformation: EnvironmentInformation) {
    val connectionInfo = ConnectionParameters(
        conductance = environmentInformation.airThermalConductivity
    )

    this.connect(a, environmentInformation.temperature, connectionInfo)
}

fun Simulator.connect(a: ThermalBody, environmentInformation: EnvironmentInformation) {
    this.connect(a.thermal, environmentInformation)
}
