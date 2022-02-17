@file:Suppress("UNUSED_PARAMETER", "unused")

package org.eln2.mc.utility

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
