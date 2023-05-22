package org.eln2.mc.extensions

import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.ageseries.libage.sim.electrical.mna.component.VoltageSource
import org.eln2.mc.integration.WailaTooltipBuilder

fun WailaTooltipBuilder.resistor(resistor: Resistor): WailaTooltipBuilder {
    this.resistance(resistor.resistance)
    this.pinVoltages(resistor.pins)
    this.current(resistor.current)
    this.power(resistor.power)

    return this
}

fun WailaTooltipBuilder.voltageSource(source: VoltageSource): WailaTooltipBuilder {
    this.voltage(source.potential)
    this.current(source.current)

    return this
}
