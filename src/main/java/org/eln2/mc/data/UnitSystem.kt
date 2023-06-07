@file:Suppress("UNUSED_PARAMETER", "unused")

package org.eln2.mc.data

import org.ageseries.libage.sim.Scale
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
    LUX("lx")
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

@JvmInline
value class Duration(val seconds: Double) {
    fun to(scale: Scale): Double = scale.map(seconds)

    operator fun not() = seconds
    operator fun plus(rhs: Duration) = Duration(seconds + rhs.seconds)
    operator fun minus(rhs: Duration) = Duration(seconds - rhs.seconds)
    operator fun times(rhs: Double) = Duration(seconds * rhs)
    operator fun div(rhs: Double) = Duration(seconds / rhs)
    operator fun div(rhs: Duration) = seconds / rhs.seconds

    operator fun compareTo(rhs: Duration) = seconds.compareTo(rhs.seconds)

    override fun toString() = TimeUnits.SECOND.display(seconds)

    operator fun rangeTo(s: Scale) = to(s)

    companion object {
        fun from(duration: Double, scale: Scale) = Duration(scale.unmap(duration))
    }
}

@JvmInline
value class Distance(val meters: Double) {
    fun to(scale: Scale): Double = scale.map(meters)

    operator fun not() = meters
    operator fun plus(rhs: Distance) = Distance(meters + rhs.meters)
    operator fun minus(rhs: Distance) = Distance(meters - rhs.meters)
    operator fun times(rhs: Double) = Distance(meters * rhs)
    operator fun div(rhs: Double) = Distance(meters / rhs)
    operator fun div(rhs: Distance) = meters / rhs.meters


    operator fun compareTo(rhs: Distance) = meters.compareTo(rhs.meters)

    override fun toString() = DistanceUnits.METER.display(meters)

    operator fun rangeTo(s: Scale) = to(s)

    companion object {
        fun from(distance: Double, scale: Scale) = Distance(scale.unmap(distance))
    }
}

@JvmInline
value class Energy(val joules: Double) {
    fun to(scale: Scale): Double = scale.map(joules)

    operator fun not() = joules
    operator fun plus(rhs: Energy) = Energy(joules + rhs.joules)
    operator fun minus(rhs: Energy) = Energy(joules - rhs.joules)
    operator fun times(rhs: Double) = Energy(joules * rhs)
    operator fun div(rhs: Double) = Energy(joules / rhs)
    operator fun div(rhs: Energy) = joules / rhs.joules

    operator fun compareTo(rhs: Energy) = joules.compareTo(rhs.joules)
    operator fun compareTo(rhs: Double) = joules.compareTo(rhs)

    override fun toString() = EnergyUnits.JOULE.display(joules)

    operator fun rangeTo(s: Scale) = to(s)

    companion object {
        fun from(energy: Double, scale: Scale) = Energy(scale.unmap(energy))
    }
}

fun abs(v: Duration) = Duration(abs(!v))
fun abs(v: Distance) = Distance(abs(!v))
fun abs(v: Energy) = Energy(abs(!v))

object TimeUnits {
    val SECOND = Scale(1.0, 0.0, "s")
    val MILLISECOND = Scale(SECOND.factor * 1000.0, 0.0, "s")
    val MICROSECOND = Scale(MILLISECOND.factor * 1000.0, 0.0, "µs")
    val NANOSECOND = Scale(MICROSECOND.factor * 1000.0, 0.0, "ns")
    val PICOSECOND = Scale(NANOSECOND.factor * 1000.0, 0.0, "ps")
    val FEMTOSECOND = Scale(PICOSECOND.factor * 1000.0, 0.0, "fs")

    val RELS = Scale(SECOND.factor / 1.2, 0.0, "rels")
    val MILLIRELS = Scale(RELS.factor * 1000.0, 0.0, "mrels")
    val MICRORELS = Scale(MILLIRELS.factor * 1000.0, 0.0, "µrels")
    val NANORELS = Scale(MICRORELS.factor * 1000.0, 0.0, "nrels")
    val PICORELS = Scale(NANORELS.factor * 1000.0, 0.0, "prels")
    val FEMTORELS = Scale(PICORELS.factor * 1000.0, 0.0, "frels")

    val DAYS = Scale(SECOND.factor / 86400, 0.0, "days")
    val WEEKS = Scale(DAYS.factor / 7.0, 0.0, "weeks")
    val MONTHS = Scale(WEEKS.factor / 4.0, 0.0, "months")
    val YEARS = Scale(DAYS.factor / 365.25, 0.0, "years")
    val DECADES = Scale(YEARS.factor / 10.0, 0.0, "decades")
    val CENTURIES = Scale(DECADES.factor / 10.0, 0.0, "centuries")
    val MILLENNIA = Scale(CENTURIES.factor / 10.0, 0.0, "millennia")
}

object DistanceUnits {
    val METER = Scale(1.0, 0.0, "m")
    val CENTIMETERS = Scale(METER.factor * 100.0, 0.0, "cm")
    val MILLIMETERS = Scale(CENTIMETERS.factor * 10.0, 0.0, "mm")

    val KILOMETERS = Scale(METER.factor / 1000.0, 0.0, "km")

    val EARTH_RADII = Scale(KILOMETERS.factor / 6.371, 0.0, "Earth radii")
    val EARTH_DIAMETERS = Scale(EARTH_RADII.factor / 2.0, 0.0, "Earth diameters")

    val STARGATE_MW_EXT_RADII = Scale(METER.factor / 6.7056, 0.0, "Milky Way Stargate exterior radii")
    val STARGATE_MW_INT_RADII = Scale(METER.factor / 4.8768, 0.0, "Milky Way Stargate interior ring radii")

    val KLIKS = Scale(KILOMETERS.factor, 0.0, "klicks")
    val MILES = Scale(KILOMETERS.factor / 1.60934, 0.0, "mi")
    val NAUTICAL_MILES = Scale(KILOMETERS.factor / 1.852, 0.0, "nmi")

    val ALTERRAN_DEKMAS = Scale(MILES.factor * (196.0 / 512.0), 0.0, "dekmas")

    val LIGHT_NANOSECONDS = Scale(METER.factor / 0.299792458, 0.0, "lns")
    val LIGHT_MICROSECONDS = Scale(LIGHT_NANOSECONDS.factor / 1000.0, 0.0, "lµs")
    val LIGHT_MILLISECONDS = Scale(LIGHT_MICROSECONDS.factor / 1000.0, 0.0, "lms")
    val LIGHT_SECONDS = Scale(LIGHT_MILLISECONDS.factor / 1000.0, 0.0, "ls")

    val LIGHT_NANORELS = Scale(LIGHT_NANOSECONDS.factor * TimeUnits.RELS.factor, 0.0, "lnrels")
    val LIGHT_MICRORELS = Scale(LIGHT_NANORELS.factor / 1000.0, 0.0, "lµrels")
    val LIGHT_MILLIRELS = Scale(LIGHT_MICRORELS.factor / 1000.0, 0.0, "lmrels")
    val LIGHT_RELS = Scale(LIGHT_MILLIRELS.factor / 1000.0, 0.0, "lrels")

    val FOOTBALL_FIELDS = Scale(METER.factor / 109.728, 0.0,  "american football fields")

    val ANGSTROM = Scale(METER.factor * 1e+10, 0.0, "Å")
}

object EnergyUnits {
    val JOULE = Scale(1.0, 0.0, "J")
    val KILOJOULES = Scale(JOULE.factor / 1000.0, 0.0, "kJ")
    val MEGAJOULES = Scale(KILOJOULES.factor / 1000.0, 0.0, "MJ")
    val GIGAJOULES = Scale(MEGAJOULES.factor / 1000.0, 0.0, "GJ")

    val WATT_SECONDS = Scale(JOULE.factor, 0.0, "Ws")
    val WATT_MINUTES = Scale(WATT_SECONDS.factor / 60.0, 0.0, "Wmin")
    val WATT_HOURS = Scale(WATT_MINUTES.factor / 60.0, 0.0, "Wh")
    val KW_HOURS = Scale(WATT_HOURS.factor / 1000.0, 0.0, "KWh")

    val WATT_RELS = Scale(JOULE.factor / TimeUnits.RELS.factor, 0.0, "Wrels")
}
