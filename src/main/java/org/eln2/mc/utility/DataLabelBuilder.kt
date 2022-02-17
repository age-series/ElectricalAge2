package org.eln2.mc.utility

import kotlin.math.abs

class DataLabelBuilder {
    open class Entry(open val label : String, open val value : String, open val color : McColor)

    private class SiEntry(override val label : String, val number : Double, val decimals : Int, val unit : String, override val color : McColor)
        : Entry(label, SuffixConverter.convert(abs(number), unit, decimals), color)

    val entries = ArrayList<Entry>()

    fun entry(label : String, value : String, color : McColor) : DataLabelBuilder{
        entries.add(Entry(label, value, color))
        return this
    }

    fun entry(entry: Entry) : DataLabelBuilder{
        entries.add(entry)
        return this
    }

    fun siEntry(label: String, suffix : String, value: Double, color : McColor, decimals : Int = 2) : DataLabelBuilder{
        return entry(SiEntry(label, value, decimals, suffix,  color))
    }

    fun volts(voltage : Double, color : McColor) : DataLabelBuilder {
        return entry(SiEntry("Voltage", voltage, 2, "V", color))
    }

    fun ohms(resistance : Double, color : McColor) : DataLabelBuilder {
        return entry(SiEntry("Resistance", resistance, 2, "Ω", color))
    }

    fun amps(current : Double, color : McColor) : DataLabelBuilder {
        return entry(SiEntry("Current", current, 2, "A", color))
    }

    fun joules(energy : Double, color : McColor) : DataLabelBuilder {
        return entry(SiEntry("Energy", energy, 2, "J", color))
    }

    fun farads(capacitance : Double, color : McColor) : DataLabelBuilder {
        return entry(SiEntry("Capacitance", capacitance, 2, "F", color))
    }

    fun henry(inductance : Double, color : McColor) : DataLabelBuilder {
        return entry(SiEntry("Inductance", inductance, 2, "H", color))
    }

    fun <T> enumerateOn(enumeration: List<T>, editor: (T, DataLabelBuilder) -> Unit) : DataLabelBuilder{
        for(x in enumeration){
            editor(x, this)
        }

        return this
    }
}

