package org.eln2.mc.extensions

import org.eln2.libelectric.sim.electrical.mna.component.Capacitor
import org.eln2.libelectric.sim.electrical.mna.component.Inductor
import org.eln2.libelectric.sim.electrical.mna.component.Resistor
import org.eln2.libelectric.sim.electrical.mna.component.VoltageSource
import org.eln2.mc.utility.DataLabelBuilder
import org.eln2.mc.utility.McColor
import org.eln2.mc.utility.McColors

object DataLabelBuilderExtensions {
    fun DataLabelBuilder.of(resistor : Resistor) : DataLabelBuilder{
        return this
            .amps(resistor.current, McColors.red)
            .ohms(resistor.resistance, McColors.cyan)
    }

    fun DataLabelBuilder.of(capacitor: Capacitor) : DataLabelBuilder{
        return this
            .amps(capacitor.current, McColors.red)
            .joules(capacitor.energy, McColors.purple)
    }

    fun DataLabelBuilder.of(inductor : Inductor) : DataLabelBuilder{
        return this
            .amps(inductor.current, McColors.red)
            .joules(inductor.energy, McColors.purple)
            .henry(inductor.inductance, McColors.green)
    }

    fun DataLabelBuilder.of(voltageSource: VoltageSource) : DataLabelBuilder{
        return this
            .amps(voltageSource.current, McColors.red)
            .volts(voltageSource.potential, McColor(255u, 55u, 200u))
    }
}
