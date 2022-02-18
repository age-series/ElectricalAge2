package org.eln2.mc.extensions

import org.eln2.libelectric.sim.electrical.mna.component.Capacitor
import org.eln2.libelectric.sim.electrical.mna.component.Inductor
import org.eln2.libelectric.sim.electrical.mna.component.Resistor
import org.eln2.libelectric.sim.electrical.mna.component.VoltageSource
import org.eln2.mc.utility.*
import kotlin.math.abs

object DataBuilderExtensions {
    private class InternationalSystemEntry(override val label : String, val number : Double, val decimals : Int, val unit : String)
        : DataBuilder.Entry(label, SuffixConverter.convert(abs(number), unit, decimals))

    fun DataBuilder.siEntry(label: String, suffix : String, value: Double, decimals : Int = 2) : DataBuilder{
        return entry(InternationalSystemEntry(label, value, decimals, suffix))
    }

    fun DataBuilder.volts(voltage : Double) : DataBuilder {
        return entry(InternationalSystemEntry("Voltage", voltage, 2, "V"))
    }

    fun DataBuilder.ohms(resistance : Double) : DataBuilder {
        return entry(InternationalSystemEntry("Resistance", resistance, 2, "Ω"))
    }

    fun DataBuilder.amps(current : Double) : DataBuilder {
        return entry(InternationalSystemEntry("Current", current, 2, "A"))
    }

    fun DataBuilder.joules(energy : Double) : DataBuilder {
        return entry(InternationalSystemEntry("Energy", energy, 2, "J"))
    }

    fun DataBuilder.farads(capacitance : Double) : DataBuilder {
        return entry(InternationalSystemEntry("Capacitance", capacitance, 2, "F"))
    }

    fun DataBuilder.henry(inductance : Double) : DataBuilder {
        return entry(InternationalSystemEntry("Inductance", inductance, 2, "H"))
    }

    fun DataBuilder.of(resistor : Resistor, useColors : Boolean = true) : DataBuilder{
        return if(!useColors) this.amps(resistor.current).ohms(resistor.resistance)
        else this
            .amps(resistor.current).withAmpsLabelColor().withElectricalValueColor()
            .ohms(resistor.resistance).withOhmsLabelColor().withElectricalValueColor()

    }

    fun DataBuilder.of(capacitor: Capacitor, useColors : Boolean = true) : DataBuilder{
        return if(!useColors) this.amps(capacitor.current).joules(capacitor.energy)
        else this
            .amps(capacitor.current).withAmpsLabelColor().withElectricalValueColor()
            .joules(capacitor.energy).withJoulesLabelColor().withElectricalValueColor()
    }

    fun DataBuilder.of(inductor : Inductor, useColors : Boolean = true) : DataBuilder{
        return if(!useColors) this
            .amps(inductor.current)
            .joules(inductor.energy)
            .henry(inductor.inductance)
        else this
            .amps(inductor.current).withAmpsLabelColor().withElectricalValueColor()
            .joules(inductor.energy).withJoulesLabelColor().withElectricalValueColor()
            .henry(inductor.inductance).withHenryLabelColor().withElectricalValueColor()
    }

    fun DataBuilder.of(voltageSource: VoltageSource, useColors : Boolean = true) : DataBuilder{
        return if(!useColors) this
            .amps(voltageSource.current)
            .volts(voltageSource.potential)
        else this
            .amps(voltageSource.current).withAmpsLabelColor().withElectricalValueColor()
            .volts(voltageSource.potential).withVoltsLabelColor().withElectricalValueColor()
    }

    fun <T> DataBuilder.enumerateOn(enumeration: List<T>, editor: (T, DataBuilder, index : Int) -> Unit) : DataBuilder {
        enumeration.forEachIndexed { index, x -> editor(x, this, index) }
        return this
    }

    fun DataBuilder.withLabelColor(color : McColor) : DataBuilder{
        if(this.entries.isEmpty()){
            throw Exception("Cannot apply color: the builder doesn't have any entries!")
        }

        val last = this.entries.removeLast()
        entry(DataBuilder.Entry(ColoredStringFormatter.addColor(last.label, color), last.value))

        return this
    }

    fun DataBuilder.withValueColor(color : McColor) : DataBuilder{
        if(this.entries.isEmpty()){
            throw Exception("Cannot apply color: the builder doesn't have any entries!")
        }

        val last = this.entries.removeLast()
        entry(DataBuilder.Entry(last.label, ColoredStringFormatter.addColor(last.value, color)))

        return this
    }

    fun DataBuilder.withAmpsLabelColor() : DataBuilder {return this.withLabelColor(McColors.red)}
    fun DataBuilder.withVoltsLabelColor() : DataBuilder {return this.withLabelColor(McColors.cyan)}
    fun DataBuilder.withOhmsLabelColor() : DataBuilder {return this.withLabelColor(McColors.yellow)}
    fun DataBuilder.withJoulesLabelColor() : DataBuilder {return this.withLabelColor(McColors.purple)}
    fun DataBuilder.withHenryLabelColor() : DataBuilder {return this.withLabelColor(McColor(255u, 170u, 80u))}

    fun DataBuilder.withElectricalValueColor() : DataBuilder {return this.withValueColor(McColors.green)}
}
