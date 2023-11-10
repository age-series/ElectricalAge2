@file:Suppress("UNUSED_PARAMETER", "unused")

package org.eln2.mc.data

import org.ageseries.libage.data.*
import org.ageseries.libage.sim.Scale
import org.ageseries.libage.sim.thermal.ThermalUnits
import org.eln2.mc.formatted
import org.eln2.mc.map
import org.eln2.mc.mathematics.Dual
import org.eln2.mc.mathematics.approxEq
import org.eln2.mc.unmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * @param unit The unit type (eg, for amps, A)
 *
 * // https://www.britannica.com/science/International-System-of-Units
 */
enum class UnitType(val unit: String) {
    METRE("m"),
    SECOND("s"),
    GRAM("g"),
    AMPERE("A"),
    KELVIN("K"),
    CANDELA("cd"),
    MOLE("mol"),
    SQUARE_METRE("m²"),
    CUBIC_METRE("m³"),
    LITRE("L"),
    HERTZ("Hz"),
    NEWTON("N"),
    JOULE("J"),
    PASCAL("Pa"),
    WATT("W"),
    COULOMB("C"),
    VOLT("V"),
    FARAD("F"),
    OHM("Ω"),
    SIEMENS("S"),
    WEBER("Wb"),
    TESLA("T"),
    HENRY("H"),
    LUMEN("lm"),
    LUX("lx"),

    GRAY("Gy"),
    SIEVERT("Sv")
}

/**
 * @param prefix The prefix symbol
 * @param factor The number to multiply by
 *
 * https://www.nist.gov/pml/weights-and-measures/metric-si-prefixes
 */
enum class MetricPrefix(prefix: String, factor: Double) {
    TERA("T", 1000000000000.0),
    GIGA("G", 1000000000.0),
    MEGA("M", 1000000.0),
    KILO("k", 1000.0),
    HECTO("h", 100.0),
    DEKA("da", 10.0),
    DECI("d", 0.1),
    CENTI("c", 0.01),
    MILLI("m", 0.001),
    MICRO("μ", 0.000001),
    NANO("n", 0.000000001),
    PICO("p", 0.000000000001)
}

fun valueText(value: Double, baseUnit: UnitType): String {
    val valueAbs = abs(value)
    return when {
        valueAbs < 0.0000001 ->
            "0"

        valueAbs < 0.000999 ->
            String.format("%1.2fµ", value * 1000000)

        valueAbs < 0.00999 ->
            String.format("%1.2fm", value * 1000)

        valueAbs < 0.0999 ->
            String.format("%2.1fm", value * 1000)

        valueAbs < 0.999 ->
            String.format("%3.0fm", value * 1000)

        valueAbs < 9.99 ->
            String.format("%1.2f", value)

        valueAbs < 99.9 ->
            String.format("%2.1f", value)

        valueAbs < 999 ->
            String.format("%3.0f", value)

        valueAbs < 9999 ->
            String.format("%1.2fk", value / 1000.0)

        valueAbs < 99999 ->
            String.format("%2.1fk", value / 1000.0)

        else -> // if(value < 1000000)
            String.format("%3.0fk", value / 1000.0)
    } + baseUnit.unit
}

/**
 * Represents a physical quantity, characterised by a [Unit] and a dual number [values].
 * */
data class QuantityDual<Unit>(val values: Dual) {
    constructor(quantity: Dual, s: QuantityScale<Unit>) : this(s.scale.unmap(quantity))

    val isReal get() = values.isReal

    /**
     * Gets the real component of the quantity.
     * */
    val value get() = values.value

    fun head(n: Int = 1) = values.head(n)
    fun tail(n: Int = 1) = values.tail(n)

    /**
     * Gets the dual value of this quantity.
     * */
    operator fun not() = values
    operator fun plus(b: QuantityDual<Unit>) = QuantityDual<Unit>(this.values + b.values)
    operator fun plus(b: Quantity<Unit>) = QuantityDual<Unit>(this.values + b.value)
    operator fun minus(b: QuantityDual<Unit>) = QuantityDual<Unit>(this.values - b.values)
    operator fun minus(b: Quantity<Unit>) = QuantityDual<Unit>(this.values - b.value)
    operator fun times(scalar: Dual) = QuantityDual<Unit>(this.values * scalar)
    operator fun times(scalar: Double) = QuantityDual<Unit>(this.values * scalar)
    operator fun div(scalar: Dual) = QuantityDual<Unit>(this.values / scalar)
    operator fun div(scalar: Double) = QuantityDual<Unit>(this.values / scalar)
    operator fun div(b: QuantityDual<Unit>) = this.values / b.values
    operator fun div(b: Quantity<Unit>) = this.values / b.value
    operator fun rangeTo(s: QuantityScale<Unit>) = s.scale.map(values)

    /**
     * Gets the [n]th value.
     * */
    operator fun get(n: Int) = values[n]
}

fun <U> min(a: Quantity<U>, b: Quantity<U>) = Quantity<U>(min(!a, !b))
fun <U> max(a: Quantity<U>, b: Quantity<U>) = Quantity<U>(max(!a, !b))
fun <U> abs(q: Quantity<U>) = Quantity<U>(abs(!q))

fun parseTimeUnitOrNull(unit: String) = when (unit) {
    "days" -> DAYS
    "day" -> DAYS
    "d" -> DAYS
    "hours" -> HOURS
    "hour" -> HOURS
    "hrs" -> HOURS
    "hr" -> HOURS
    "h" -> HOURS
    "minutes" -> MINUTES
    "minute" -> MINUTES
    "min" -> MINUTES
    "seconds" -> SECOND
    "second" -> SECOND
    "sec" -> SECOND
    "s" -> SECOND
    else -> null
}

fun parseTimeUnit(unit: String) = parseTimeUnitOrNull(unit) ?: error("Unrecognised time unit $unit")

fun parseTempUnitOrNull(unit: String) = when (unit) {
    "celsius" -> CELSIUS
    "°C" -> CELSIUS
    "C" -> CELSIUS
    "kelvin" -> KELVIN
    "K" -> KELVIN
    else -> null
}

fun parseTempUnit(unit: String) = parseTempUnitOrNull(unit) ?: error("Unrecognised temp unit $unit")

interface Power
val WATT = standardScale<Power>()
val KILOWATT = +WATT

interface Voltage
val VOLT = standardScale<Voltage>()
val KILOVOLT = +VOLT
val MILLIVOLT = -VOLT

interface Resistance
val OHM = standardScale<Resistance>()
val KILOOHM = +OHM
val MEGAOHM = +KILOOHM
val GIGAOHM = +MEGAOHM
val MILLIOHM = -OHM

interface Intensity
val WATT_PER_M2 = standardScale<Intensity>()

val kWh by ::KW_HOURS

val KG by ::KILOGRAMS
val eV by ::ELECTRON_VOLT
val keV by ::KILO_ELECTRON_VOLT
val MeV by ::MEGA_ELECTRON_VOLT
val GeV by ::GIGA_ELECTRON_VOLT
val TeV by ::TERA_ELECTRON_VOLT

val Pa by ::PASCAL
val Atm by ::ATMOSPHERES

val kW by ::KILOWATT
