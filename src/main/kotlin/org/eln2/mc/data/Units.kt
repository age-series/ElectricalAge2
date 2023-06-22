@file:Suppress("UNUSED_PARAMETER", "unused")

package org.eln2.mc.data

import org.ageseries.libage.sim.Scale
import org.eln2.mc.formatted
import org.eln2.mc.map
import org.eln2.mc.mathematics.Dual
import org.eln2.mc.unmap
import kotlin.math.abs

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

data class QuantityScale<Unit>(val scale: Scale) {
    constructor(factor: Double, base: Double) : this(
        Scale(factor, base)
    )

    val base get() = scale.base
    val factor get() = scale.factor

    operator fun times(mult: Double) = QuantityScale<Unit>(
        scale.factor / mult,
        scale.base
    )

    operator fun div(submult: Double) = QuantityScale<Unit>(
        scale.factor * submult,
        scale.base
    )

    operator fun unaryPlus() = this * 1000.0
    operator fun unaryMinus() = this / 1000.0
}

interface Mass
interface Time
interface Distance
interface Velocity
interface Energy
interface Radioactivity
interface RadiationAbsorbedDose
interface RadiationDoseEquivalent
interface RadiationExposure
interface ArealDensity
interface ReciprocalDistance
interface ReciprocalArealDensity
interface Density
interface Substance
interface Volume

@JvmInline
value class Quantity<Unit>(val value: Double) : Comparable<Quantity<Unit>> {
    constructor(quantity: Double, s: QuantityScale<Unit>) : this(s.scale.unmap(quantity))

    operator fun not() = value
    operator fun unaryMinus() = Quantity<Unit>(-value)
    operator fun unaryPlus() = Quantity<Unit>(+value)
    operator fun plus(b: Quantity<Unit>) = Quantity<Unit>(this.value + b.value)
    operator fun minus(b: Quantity<Unit>) = Quantity<Unit>(this.value - b.value)
    operator fun times(scalar: Double) = Quantity<Unit>(this.value * scalar)
    operator fun div(scalar: Double) = Quantity<Unit>(this.value / scalar)
    operator fun div(b: Quantity<Unit>) = this.value / b.value
    override operator fun compareTo(other: Quantity<Unit>) = value.compareTo(other.value)
    operator fun compareTo(b: Double) = value.compareTo(b)
    operator fun rangeTo(s: QuantityScale<Unit>) = s.scale.map(value)

    fun format(decimals: Int = 3, scale: QuantityScale<Unit>? = null) =
        if (scale == null) (!this).formatted(decimals)
        else (this..scale).formatted(decimals)

    override fun toString() = value.toString()

    fun <U2> reparam(factor: Double = 1.0) = Quantity<U2>(value * factor)
}

data class QuantityDual<Unit>(val values: Dual) {
    constructor(quantity: Dual, s: QuantityScale<Unit>) : this(s.scale.unmap(quantity))

    val isReal get() = values.isReal
    val value get() = values.value
    fun head(n: Int = 1) = values.head(n)
    fun tail(n: Int = 1) = values.tail(n)

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
    operator fun get(n: Int) = values[n]
}

fun <U> abs(q: Quantity<U>) = Quantity<U>(abs(!q))

private fun <U> standard(factor: Double = 1.0, base: Double = 0.0) = QuantityScale<U>(factor, base)

val KILOGRAMS = standard<Mass>()
val GRAMS = -KILOGRAMS

val SECOND = standard<Time>()
val MILLISECONDS = -SECOND
val MICROSECONDS = -MILLISECONDS
val NANOSECONDS = -MICROSECONDS
val MINUTES = SECOND * 60.0
val HOURS = MINUTES * 60.0

val METER = standard<Distance>()
val CENTIMETERS = METER / 100.0
val MILLIMETERS = -METER

val JOULE = standard<Energy>()
val KILOJOULES = +JOULE
val MEGAJOULES = +KILOJOULES
val GIGAJOULES = +MEGAJOULES
val WATT_SECONDS = QuantityScale<Energy>(JOULE.factor, 0.0)
val WATT_MINUTES = WATT_SECONDS * 60.0
val WATT_HOURS = WATT_MINUTES * 60.0
val KW_HOURS = WATT_HOURS * 1000.0

// Serious precision issues? Hope not! :Fish_Smug:
val ELECTRON_VOLT = JOULE * 1.602176634e-19
val KILO_ELECTRON_VOLT = JOULE * 1.602176634e-16
val MEGA_ELECTRON_VOLT = JOULE * 1.602176634e-13
val GIGA_ELECTRON_VOLT = JOULE * 1.602176634e-10
val TERA_ELECTRON_VOLT = JOULE * 1.602176634e-7

val BECQUEREL = standard<Radioactivity>()
val KILOBECQUERELS = +BECQUEREL
val MEGABECQUERELS = +KILOBECQUERELS
val GIGABECQUERELS = +MEGABECQUERELS
val TERABECQUERELS = +GIGABECQUERELS
val CURIE = GIGABECQUERELS * 37.0
val MILLICURIES = MEGABECQUERELS * 37.0
val MICROCURIES = KILOBECQUERELS * 37.0
val NANOCURIES = BECQUEREL * 37.0
val KILOCURIES = +CURIE
val MEGACURIES = +KILOCURIES
val GIGACURIES = +MEGACURIES // Average conversation with Grissess (every disintegration is a cute dragon image)

val GRAY = standard<RadiationAbsorbedDose>()
val RAD = GRAY / 100.0

val SIEVERT = standard<RadiationDoseEquivalent>()
val MILLISIEVERTS = -SIEVERT
val MICROSIEVERTS = -MILLISIEVERTS
val REM = SIEVERT / 100.0
val MILLIREM = -REM
val MICROREM = -MILLIREM

val COULOMB_PER_KG = standard<RadiationExposure>()
val ROENTGEN = COULOMB_PER_KG / 3875.96899225

val RECIP_METER = standard<ReciprocalDistance>()
val RECIP_CENTIMETERS = RECIP_METER * 100.0

val KG_PER_M2 = standard<ArealDensity>()
val G_PER_CM2 = KG_PER_M2 * 10.0

val KG_PER_M3 = standard<Density>()
val G_PER_CM3 = KG_PER_M3 * 1000.0

val M2_PER_KG = standard<ReciprocalArealDensity>()
val CM2_PER_G = M2_PER_KG / 10.0

val M_PER_S = standard<Velocity>()
val KM_PER_S = +M_PER_S

val MOLE = standard<Substance>()

val M3 = standard<Volume>()
val LITERS = M3 / 1000.0
val MILLILITERS = -LITERS

operator fun <U> Quantity<U>.rangeTo(other: Quantity<U>) = ClosedQuantityRange(this, other)

data class ClosedQuantityRange<T>(override val start: Quantity<T>, override val endInclusive: Quantity<T>) :
    ClosedRange<Quantity<T>> {
    val valueRange = !start..!endInclusive
    override fun contains(value: Quantity<T>) = (!value) in valueRange
    override fun isEmpty(): Boolean = valueRange.isEmpty()
}

val eV by ::ELECTRON_VOLT
val keV by ::KILO_ELECTRON_VOLT
val MeV by ::MEGA_ELECTRON_VOLT
val GeV by ::GIGA_ELECTRON_VOLT
val TeV by ::TERA_ELECTRON_VOLT
