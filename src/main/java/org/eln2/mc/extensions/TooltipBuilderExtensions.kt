package org.eln2.mc.extensions

import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.ageseries.libage.sim.electrical.mna.component.VoltageSource
import org.eln2.mc.integration.waila.TooltipBuilder

fun TooltipBuilder.resistor(resistor: Resistor): TooltipBuilder {
    this.resistance(resistor.resistance)
    this.pinVoltages(resistor.pins)
    this.current(resistor.current)
    this.power(resistor.power)

    return this
}

fun TooltipBuilder.voltageSource(source: VoltageSource): TooltipBuilder {
    this.voltage(source.potential)
    this.current(source.current)

    return this
}
