@file:Suppress("unused")
@file:JvmName(JVM_NAME)

package org.ageseries.libage.data

import org.ageseries.libage.mathematics.rounded
import org.ageseries.libage.utils.sourceName
import kotlin.math.abs
import kotlin.math.log
import kotlin.math.pow
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.kotlinProperty

typealias ScaleRef<T> = KProperty<QuantityScale<T>>

private const val JVM_NAME = "QuantityKt"
private const val CLASS_NAME = "org.ageseries.libage.data.$JVM_NAME"

private val SELF by lazy {
    checkNotNull(Class.forName(CLASS_NAME)) {
        "Failed to resolve Quantity class $CLASS_NAME"
    }
}

/**
 * A linear scale.
 *
 * This scale suffices for most linear natural units; a natural example is temperature.
 *
 * In general, don't construct these unless you really are making a new scale; the associated
 * constant members should suffice for most use cases.
 *
 * This package usually uses a specific "base unit" in each of the simulation domains, chosen to be
 * an easy-to-compute value (e.g., unit in the kms system). Those domains have further documentation
 * on their choice of base unit, which is usually part of the documentation in or around wherever
 * these scales are defined.
 */
data class Scale(val factor: Double, val base: Double) {
    /** Given a value [u] in base unit, return its value in this unit. */
    fun map(u: Double): Double = factor * u + base

    /** Given a value [u] in this unit, return its value in base units. */
    fun unmap(u: Double): Double = (u - base) / factor
}

/**
 * [Scale], parameterized by [Unit], aligns with the physical attribute measured by the scale (such as distance, time, and so forth).
 * Notably, [Unit] remains distinct from the [scale]; for instance, [Distance] can be measured in [METER]s, feet, dragon tails, american football fields, and other units.
 * It is important to recognize that [Unit] functions merely as a compiler mechanism and holds no inherent functional significance.
 * @param dimensionType The class of the *symbolic interface* [Unit]. This is the same for all auxiliary units.
 * */
open class QuantityScale<Unit>(val dimensionType: Class<*>, val scale: Scale) {
    val factor get() = scale.factor

    /**
     * Amplifies this scale [amplify] times.
     * Example: *GRAMS * 1000* will result in *KILOGRAMS*.
     * */
    operator fun times(amplify: Double) = QuantityScale<Unit>(dimensionType, Scale(scale.factor / amplify, scale.base))

    /**
     * Reduces this scale [reduce] times.
     * Example: *KILOGRAMS / 1000* will result in *GRAMS*.
     * */
    operator fun div(reduce: Double) = QuantityScale<Unit>(dimensionType, Scale(scale.factor * reduce, scale.base))

    /**
     * Amplifies this scale 1000 times.
     * Example: *+GRAMS* will result in *KILOGRAMS*.
     * */
    operator fun unaryPlus() = this * 1000.0

    /**
     * Reduces this scale 1000 times.
     * Example: *-KILOGRAMS* will result in *GRAMS*.
     * */
    operator fun unaryMinus() = this / 1000.0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as QuantityScale<*>

        return scale == other.scale
    }

    override fun hashCode(): Int {
        return scale.hashCode()
    }
}

/**
 * Signifies a multiplication factor employed to adjust a [QuantityScale].
 * Presently, its extension is not supported; to minimize runtime cost, a lookup table is precomputed.
 * This design ensures the avoidance of additional allocations for new instances of [QuantityScale] (the composition cost boils down to accessing the precomputed instance).
 * */
class ScaleMultiplier internal constructor(val factor: Double, val index: Int) {
    /**
     * Composes the base [scale] with this multiplier, returning a scale which is equivalent to the result of *[scale] * [factor]*.
     * */
    operator fun<T> times(scale: SourceQuantityScale<T>) = scale.multiples[index]
}

/**
 * Composes the base scale with this multiplier, returning another source scale.
 * */
internal infix fun<T> ScaleMultiplier.sourceCompose(scale: SourceQuantityScale<T>) = SourceQuantityScale<T>(scale.dimensionType, scale.multiples[index].scale)

/** Transforms the units into **pico- (×10⁻¹²)** */
val PICO = ScaleMultiplier(1e-12, 0)

/** Transforms the units into **nano- (×10⁻⁹)** */
val NANO = ScaleMultiplier(1e-9, 1)

/** Transforms the units into **micro- (×10⁻⁶)** */
val MICRO = ScaleMultiplier(1e-6, 2)

/** Transforms the units into **milli- (×10⁻³)** */
val MILLI = ScaleMultiplier(1e-3, 3)

/** Transforms the units into **kilo- (×10³)** */
val KILO = ScaleMultiplier(1e3, 4)

/** Transforms the units into **nano- (×10⁶)** */
val MEGA = ScaleMultiplier(1e6, 5)

/** Transforms the units into **nano- (×10⁹)** */
val GIGA = ScaleMultiplier(1e9, 6)

/** Transforms the units into **nano- (×10¹²)** */
val TERA = ScaleMultiplier(1e12, 7)

private val MULTIPLIERS = listOf(PICO, NANO, MICRO, MILLI, KILO, MEGA, GIGA, TERA)

/**
 * [QuantityScale] that is composable with [ScaleMultiplier].
 * "Source" means it is a fundamental multiple of the standard (base) scale; normal multiplication/division yields non-source [QuantityScale] instances.
 * Yielding [SourceQuantityScale] would cause infinite recursive creation of child scales.
 * */
class SourceQuantityScale<Unit>(dimensionType: Class<*>, scale: Scale) : QuantityScale<Unit>(dimensionType, scale) {
    /**
     * Pre-computed lookup table for multiples of this scale.
     * When we apply a [ScaleMultiplier], we just fetch the pre-computed scale.
     * */
    internal val multiples = Array(MULTIPLIERS.size) { index ->
        val multiplier = MULTIPLIERS.first { it.index == index }
        // This operation creates the base [QuantityScale] which doesn't just recursively keep creating other lookup tables.
        this * multiplier.factor
    }
}

/**
 * Amplifies this scale and results in a new [SourceQuantityScale] (as opposed to [times] which results in [QuantityScale]).
 * */
infix fun <U> SourceQuantityScale<U>.sourceAmplify(amplify: Double) = SourceQuantityScale<U>(dimensionType, Scale(scale.factor / amplify, scale.base))

/**
 * Reduces this scale and results in a new [SourceQuantityScale] (as opposed to [div] which results in [QuantityScale]).
 * */
infix fun <U> SourceQuantityScale<U>.sourceReduce(reduce: Double) = SourceQuantityScale<U>(dimensionType, Scale(scale.factor * reduce, scale.base))

fun <U> SourceQuantityScale<U>.sourceAmp() = this sourceAmplify 1000.0

fun <U> SourceQuantityScale<U>.sourceSub() = this sourceReduce 1000.0

/**
 * Denotes a tangible quantity distinguished by a designated [Unit] and a numeric [value].
 * The [value] is consistently expressed in fundamental units, such as Kelvin, Meter, Kilogram, and the like.
 * This design is sufficiently straightforward to be instantiated as an inline value-class, thereby rendering the runtime overhead of carrying around [Quantity] virtually negligible.
 * The [not] operator was chosen because it is the least "ugly" way to quickly fetch the [value] for subsequent operations with [Double].
 * If you don't like it, Grissess, just access [value].
 * */
@JvmInline
value class Quantity<Unit>(val value: Double) : Comparable<Quantity<Unit>> {
    /**
     * Creates a [Quantity] where its [value] is established as the equivalent of [sourceValue] in the [standardScale].
     * */
    constructor(sourceValue: Double, scale: QuantityScale<Unit>) : this(scale.scale.unmap(sourceValue))

    val isZero get() = value == 0.0

    /**
     * Gets the [value] quickly and nicely.
     * */
    operator fun not() = value
    operator fun unaryMinus() = Quantity<Unit>(-value)
    operator fun unaryPlus() = Quantity<Unit>(+value)
    operator fun plus(b: Quantity<Unit>) = Quantity<Unit>(this.value + b.value)
    operator fun minus(b: Quantity<Unit>) = Quantity<Unit>(this.value - b.value)
    operator fun times(scalar: Double) = Quantity<Unit>(this.value * scalar)
    operator fun div(scalar: Double) = Quantity<Unit>(this.value / scalar)

    /**
     * Divides the quantity by another quantity of the same unit. This, in turn, cancels out the [Unit], returning the resulting number.
     * */
    operator fun div(b: Quantity<Unit>) = this.value / b.value

    override operator fun compareTo(other: Quantity<Unit>) = value.compareTo(other.value)

    operator fun compareTo(b: Double) = value.compareTo(b)

    /**
     * Maps this quantity to another scale of the same unit.
     * */
    operator fun rangeTo(s: QuantityScale<Unit>) = s.scale.map(value)

    override fun toString() = value.toString()
}

@JvmName("quantityPlusDouble") operator fun <U> Quantity<U>.plus(b: Double) = Quantity<U>(this.value + b)
@JvmName("quantityMinusDouble") operator fun <U> Quantity<U>.minus(b: Double) = Quantity<U>(this.value - b)
@JvmName("doublePlusQuantity") operator fun <U> Double.plus(b: Quantity<U>) = Quantity<U>(this + b.value)
@JvmName("doubleMinusQuantity") operator fun <U> Double.minus(b: Quantity<U>) = Quantity<U>(this - b.value)

fun <U> min(a: Quantity<U>, b: Quantity<U>) = Quantity<U>(kotlin.math.min(!a, !b))
fun <U> max(a: Quantity<U>, b: Quantity<U>) = Quantity<U>(kotlin.math.max(!a, !b))
fun <U> abs(q: Quantity<U>) = Quantity<U>(abs(!q))

/**
 * An iterator over a sequence of values of type [Quantity].
 * */
abstract class QuantityIterator<Unit> : Iterator<Quantity<Unit>> {
    final override fun next() = nextQuantity()

    /**
     * Returns the next value in the sequence without boxing.
     * */
    abstract fun nextQuantity() : Quantity<Unit>
}

class ArrayQuantityIterator<Unit>(private val array: QuantityArray<Unit>) : QuantityIterator<Unit>() {
    private var index = 0
    override fun hasNext() = index < array.size
    override fun nextQuantity() = try { array[index++] } catch (e: ArrayIndexOutOfBoundsException) { index -= 1; throw NoSuchElementException(e.message) }
}

/**
 * Array of quantities backed by a [DoubleArray].
 * */
class QuantityArray<Unit>(val backing: DoubleArray) {
    constructor(size: Int) : this(DoubleArray(size))
    constructor(size: Int, init: (Int) -> Quantity<Unit>) : this(DoubleArray(size) { !init(it) })
    constructor(size: Int, value: Quantity<Unit>) : this(DoubleArray(size) { !value })

    operator fun get(index: Int): Quantity<Unit> = Quantity(backing[index])

    operator fun set(index: Int, value: Quantity<Unit>) { backing[index] = !value }

    val size get() = backing.size

    operator fun iterator() = ArrayQuantityIterator(this)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as QuantityArray<*>

        return backing.contentEquals(other.backing)
    }

    override fun hashCode(): Int {
        return backing.contentHashCode()
    }
}

/**
 * Categorizes the given numerical value into a scale and represents it using a corresponding scale factor.
 * A scale factor (such as "kilo," "mega," etc.) is selected from a predefined list based on the magnitude of the given value in the specified base.
 * The chosen scale factor is inserted into the designated position marked by '#' within the unit string.
 * If '#' does not exist, it will be implied that the multiple shall be inserted at the start of the string.
 * [symbol] can also be left empty (you'll still get the number in formatted form and suffixed with the multiple).
 * Examples:
 * ```
 * classifyIntoMultiple(
 *      value = 1500.0,
 *      base = 1000.0,
 *      map = listOf(1.0 to "kilo"),
 *      symbol = "meters"
 * ) // 1.5 kilometers
 *
 * classifyIntoMultiple(
 *       value = 10.0,
 *       base = 1000.0,
 *       map = listOf(1.0 to "kilo"),
 *       symbol = "meters"
 *  ) // 10.0 meters (because inferFist is true by default)
 *
 *  classifyIntoMultiple(
 *       value = 10.0,
 *       base = 1000.0,
 *       map = listOf(1.0 to "kilo"),
 *       symbol = "meters",
 *       inferFist = false
 *  ) // 0.01 kilometers (because inferFist is false)
 *
 * classifyIntoMultiple(
 *      value = 10.0,
 *      base = 1000.0,
 *      map = listOf(1.0 to "kilo", 0.0 to "*"),
 *      symbol = "meters",
 *      inferFist = false
 * ) // 10.0 *meters
 *
 * // Trying out non-integer magnitudes is left as an exercise to the reader.
 * ```
 * @param value The value to classify.
 * @param base The base to use. Usually, this is 10 or 1000.
 * @param map A map of magnitudes *to* multiplier. Not actually a [Map] because the algorithm scans the entire map to find the best multiplier (justified because we don't care)
 * @param symbol The unit to place after the multiplier.
 * @param decimals The number of decimals to round the final value to.
 * @param inferFirst If true, no multiple will be prepended to the [symbol], if the value is around magnitude 0.
 * */
fun classifyIntoMultiple(value: Double, base: Double, map: List<Pair<Double, String>>, symbol: String, decimals: Int = 2, inferFirst: Boolean = true) : String {
    if(value < 0.0) {
        return "-${classifyIntoMultiple(-value, base, map, symbol)}"
    }

    val magnitude = log(value, base)

    var (targetMagnitude, prefix) = map
        .filter { it.first <= magnitude }
        .maxByOrNull { it.first } ?:
    map.minByOrNull { abs(it.first - magnitude) }!!

    if(inferFirst) {
        if(0.0 <= magnitude && abs(magnitude - targetMagnitude) > abs(magnitude)) {
            prefix = ""
            targetMagnitude = 0.0
        }
    }

    val multiple = if(symbol.isNotEmpty()) {
        if(symbol.contains("#")) {
            symbol.replace("#", prefix)
        }
        else {
            prefix + symbol
        }
    }
    else {
        prefix
    }

    return "${(value / base.pow(targetMagnitude)).rounded(decimals)} $multiple"
}

/**
 * Describes the base of classification base and multiples.
 * @param value The classification base.
 * @param multiples Impromptu map of magnitudes to the desired multiple.
 * */
enum class ScaleMultiples(val value: Double, val multiples: List<Pair<Double, String>>) {
    Base1000Standard(
        1000.0,
        listOf(
            -1.0 to "m", -2.0 to "µ", -3.0 to "n", -4.0 to "p", -5.0 to "f", -6.0 to "a", -7.0 to "z", -8.0 to "y",
            1.0 to "k", 2.0 to "M", 3.0 to "G", 4.0 to "T", 5.0 to "P", 6.0 to "E", 7.0 to "Z", 8.0 to "Y"
        )
    ),
    Base1000Long(
        1000.0,
        listOf(
            -1.0 to "milli",
            -2.0 to "micro",
            -3.0 to "nano",
            -4.0 to "pico",
            -5.0 to "femto",
            -6.0 to "atto",
            -7.0 to "zepto",
            -8.0 to "yocto",
            1.0 to "kilo",
            2.0 to "mega",
            3.0 to "giga",
            4.0 to "tera",
            5.0 to "peta",
            6.0 to "exa",
            7.0 to "zetta",
            8.0 to "yotta"
        )
    ),
    Base10Standard(
        10.0,
        listOf(
            -1.0 to "d",
            -2.0 to "c",
            -3.0 to "m",
            -6.0 to "µ",
            -9.0 to "n",
            -12.0 to "p",
            -15.0 to "f",
            -18.0 to "a",
            -21.0 to "z",
            -24.0 to "y",
            1.0 to "da",
            2.0 to "h",
            3.0 to "k",
            6.0 to "M",
            9.0 to "G",
            12.0 to "T",
            15.0 to "P",
            18.0 to "E",
            21.0 to "Z",
            24.0 to "Y"
        )
    ),
    Base10Long(
        10.0,
        listOf(
            -1.0 to "deci",
            -2.0 to "centi",
            -3.0 to "milli",
            -6.0 to "micro",
            -9.0 to "nano",
            -12.0 to "pico",
            -15.0 to "femto",
            -18.0 to "atto",
            -21.0 to "zepto",
            -24.0 to "yocto",
            1.0 to "deka",
            2.0 to "hecto",
            3.0 to "kilo",
            6.0 to "mega",
            9.0 to "giga",
            12.0 to "tera",
            15.0 to "peta",
            18.0 to "exa",
            21.0 to "zetta",
            24.0 to "yotta"
        )
    )
}

/**
 * Classifies this physical dimension with the [symbol] (e.g. *"#m"* for meters). The symbol *#* is where the multiple will be placed.
 * @param factor An adjustment factor for special cases. E.G. for [KILOGRAM], this factor is set to *1000* and the symbol is "g".
 * @param base The base to use. This specifies the prefixes (e.g. "kilo", "mega", ...) to place in front of the [symbol] when [classifyIntoMultiple]ing.
 * */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DimensionClassifier(
    val symbol: String,
    val factor: Double = 1.0,
    val base: ScaleMultiples = ScaleMultiples.Base1000Standard
)

/**
 * Auxiliary classifier for scales. It is applied to [QuantityScale] fields.
 * This is used to override the default classification (e.g. using a config option downstream).
 * @param identifiers Override names for this auxiliary scale. These will be used as identifiers instead of [symbol]. Use this when the symbol cannot be typed (e.g. Å)
 * */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ScaleClassifier(
    val symbol: String,
    vararg val identifiers: String,
    val factor: Double = 1.0,
    val base: ScaleMultiples = ScaleMultiples.Base1000Standard
)

/**
 * Defines the standard scale of the [Unit] (a scale with factor 1).
 * */
inline fun <reified Unit> standardScale(factor: Double = 1.0) = SourceQuantityScale<Unit>(
    Unit::class.java,
    Scale(factor, 0.0)
)

@DimensionClassifier("g", factor = 1000.0) interface Mass
val KILOGRAM = standardScale<Mass>()
@ScaleClassifier("g") val GRAM = KILOGRAM.sourceSub()
@ScaleClassifier("gr") val GRAIN = KILOGRAM sourceAmplify 6.479891e-5
@ScaleClassifier("oz") val OUNCE = KILOGRAM sourceAmplify 0.0283495231
@ScaleClassifier("st") val STONE = KILOGRAM sourceAmplify 6.35029318
@ScaleClassifier("lb") val POUND = KILOGRAM sourceAmplify 0.45359237
@ScaleClassifier("t") val TON = KILOGRAM sourceAmplify 907.18474

@ScaleClassifier("firkin") val FIRKIN = KILOGRAM sourceAmplify 40.8233

@DimensionClassifier("Da") interface AtomicMass
val DALTON = standardScale<AtomicMass>()

/**
 * Gets the atomic mass expressed in [KILOGRAM].
 * The result may not be useful due to the limited precision we're working with.
 * */
fun Quantity<AtomicMass>.asStandardMass() = Quantity(!this * 1.66053906660e-27, KILOGRAM)

@DimensionClassifier("s") interface Time
val SECOND = standardScale<Time>()
@ScaleClassifier("rel", "rels") val REL = SECOND sourceAmplify 1.2
@ScaleClassifier("min") val MINUTE = SECOND sourceAmplify 60.0
@ScaleClassifier("h") val HOUR = MINUTE sourceAmplify 60.0
@ScaleClassifier("d") val DAY = HOUR sourceAmplify 24.0
@ScaleClassifier("fortnight") val FORTNIGHT = SECOND sourceAmplify 1209600.0

@DimensionClassifier("Hz") interface Frequency
val HERTZ = standardScale<Frequency>()

@DimensionClassifier("m") interface Distance
val METER = standardScale<Distance>()
@ScaleClassifier("cm") val CENTIMETER = METER sourceReduce 100.0
@ScaleClassifier("ft") val FOOT = METER sourceAmplify 0.3048
@ScaleClassifier("in") val INCH = METER sourceAmplify 0.0254
@ScaleClassifier("yd") val YARD = METER sourceAmplify 0.9144
@ScaleClassifier("mi") val MILE = METER sourceAmplify 1609.344

@ScaleClassifier("light-nanoseconds") val LIGHT_NANOSECONDS = CENTIMETER sourceAmplify 29.9792458
@ScaleClassifier("attoparsec") val ATTO_PARSEC = CENTIMETER sourceAmplify 3.086
@ScaleClassifier("metric foot") val METRIC_FOOT = (MILLI sourceCompose METER) sourceAmplify 300.0
@ScaleClassifier("cubit") val CUBIT = CENTIMETER sourceAmplify  120.0
@ScaleClassifier("furlong") val FURLONG = METER sourceAmplify 201.168
@ScaleClassifier("football field") val AMERICAN_FOOTBALL_FIELD = METER sourceAmplify 91.44
@ScaleClassifier("Å", "A") val ANGSTROM = METER sourceReduce 1e10

@DimensionClassifier("K") interface Temperature
val KELVIN = standardScale<Temperature>()
@ScaleClassifier("°F", "F") val FAHRENHEIT = SourceQuantityScale<Temperature>(Temperature::class.java, Scale(9.0 / 5.0, -459.67))
@ScaleClassifier("Rk") val RANKINE = KELVIN sourceAmplify 0.555556 // Not R (Roentgen)
@ScaleClassifier("°C", "C") val CELSIUS = SourceQuantityScale<Temperature>(Temperature::class.java, Scale(1.0, -273.15))
@ScaleClassifier("mG") val MILLIGRADE = SourceQuantityScale<Temperature>(Temperature::class.java, Scale(10.0, -2731.5))
@ScaleClassifier("G") val GRADE = SourceQuantityScale<Temperature>(Temperature::class.java, Scale(0.01, -2.7315))
@ScaleClassifier("|G|") val ABSOLUTE_GRADE = SourceQuantityScale<Temperature>(Temperature::class.java, Scale(0.01, 0.0))

@DimensionClassifier("J") interface Energy
val JOULE = standardScale<Energy>()
@ScaleClassifier("BTU") val BTU = JOULE sourceAmplify 1055.05585262
@ScaleClassifier("cal") val CALORIE = JOULE sourceAmplify 4.18400
@ScaleClassifier("kcal") val KILOCALORIE = CALORIE.sourceAmp()
@ScaleClassifier("Ws") val WATT_SECOND = JOULE
@ScaleClassifier("Wmin") val WATT_MINUTE = WATT_SECOND sourceAmplify  60.0
@ScaleClassifier("Wh") val WATT_HOUR = WATT_MINUTE sourceAmplify 60.0

@ScaleClassifier("erg") val ERG = JOULE sourceAmplify 1e-7
@ScaleClassifier("eV") val ELECTRON_VOLT = JOULE sourceAmplify  1.602176634e-19 // Serious precision issues? Hope not! :Fish_Smug:

@DimensionClassifier("W") interface Power
val WATT = standardScale<Power>()
@ScaleClassifier("BTU/s") val BTU_PER_SECOND = WATT sourceAmplify 1055.05585
@ScaleClassifier("cal/s") val CALORIE_PER_SECOND = WATT sourceAmplify 4.18400
@ScaleClassifier("kcal/s") val KILOCALORIE_PER_SECOND = WATT sourceAmplify 4184.0
@ScaleClassifier("hp") val HORSEPOWER = WATT sourceAmplify 745.699872

@DimensionClassifier("g/m³", factor = 1000.0) interface Density
val KILOGRAM_PER_METER3 = standardScale<Density>()
@ScaleClassifier("g/cm³") val G_PER_CM3 = KILOGRAM_PER_METER3 sourceAmplify 1000.0
@ScaleClassifier("g/L") val G_PER_L = KILOGRAM_PER_METER3

@DimensionClassifier("m²") interface Area
val METER2 = standardScale<Area>()

@DimensionClassifier("m³") interface Volume
val METER3 = standardScale<Volume>()
@ScaleClassifier("L") val LITER = METER3.sourceSub()
@ScaleClassifier("gal") val GALLON = METER3 sourceAmplify 0.003785411784

@DimensionClassifier("Bq") interface Radioactivity
val BECQUEREL = standardScale<Radioactivity>()
@ScaleClassifier("Ci") val CURIE = (GIGA sourceCompose BECQUEREL) sourceAmplify 37.0

@DimensionClassifier("Gy") interface RadiationAbsorbedDose
val GRAY = standardScale<RadiationAbsorbedDose>()
@ScaleClassifier("rad") val RAD = GRAY sourceReduce 100.0

@DimensionClassifier("Sv") interface RadiationDoseEquivalent
val SIEVERT = standardScale<RadiationDoseEquivalent>()
@ScaleClassifier("rem") val REM = SIEVERT sourceReduce 100.0

@DimensionClassifier("C/kg") interface RadiationExposure
val COULOMB_PER_KILOGRAM = standardScale<RadiationExposure>()
@ScaleClassifier("R") val ROENTGEN = COULOMB_PER_KILOGRAM sourceReduce 3875.96899225

@DimensionClassifier("1/m") interface ReciprocalDistance
val RECIP_METER = standardScale<ReciprocalDistance>()
val RECIP_CENTIMETER = RECIP_METER sourceAmplify 100.0

interface ArealDensity
val KILOGRAM_PER_METER2 = standardScale<ArealDensity>()
val GRAM_PER_CENTIMETER2 = KILOGRAM_PER_METER2 sourceAmplify 10.0

interface ReciprocalArealDensity
val METER2_PER_KILOGRAM = standardScale<ReciprocalArealDensity>()
val CENTIMETER2_PER_GRAM = METER2_PER_KILOGRAM sourceReduce 10.0

@DimensionClassifier("m/s") interface Velocity
val METER_PER_SECOND = standardScale<Velocity>()

@DimensionClassifier("mol") interface Substance
val MOLE = standardScale<Substance>()

interface MolarConcentration
val MOLE_PER_METER3 = standardScale<MolarConcentration>()

@DimensionClassifier("J/kgK") interface SpecificHeatCapacity
val JOULE_PER_KILOGRAM_KELVIN = standardScale<SpecificHeatCapacity>()
@ScaleClassifier("J/gK") val JOULE_PER_GRAM_KELVIN = JOULE_PER_KILOGRAM_KELVIN.sourceAmp()
@ScaleClassifier("kJ/kgK") val KILOJOULE_PER_KILOGRAM_KELVIN = JOULE_PER_KILOGRAM_KELVIN.sourceAmp()

@DimensionClassifier("J/K") interface HeatCapacity
val JOULE_PER_KELVIN = standardScale<HeatCapacity>()

@DimensionClassifier("W/mK") interface ThermalConductivity
val WATT_PER_METER_KELVIN = standardScale<ThermalConductivity>()

@DimensionClassifier("W/K") interface ThermalConductance
val WATT_PER_KELVIN = standardScale<ThermalConductance>()

@DimensionClassifier("Ωm") interface ElectricalResistivity
val OHM_METER = standardScale<ElectricalResistivity>()

@DimensionClassifier("kg/mol") interface MolecularWeight
val KILOGRAM_PER_MOLE = standardScale<MolecularWeight>()
@ScaleClassifier("g/mol") val GRAM_PER_MOLE = KILOGRAM_PER_MOLE.sourceSub()

@DimensionClassifier("Pa") interface Pressure
val PASCAL = standardScale<Pressure>()
@ScaleClassifier("Atm") val ATMOSPHERE = PASCAL sourceAmplify 9.86923e-6

@DimensionClassifier("W/m²") interface Intensity
val WATT_PER_METER2 = standardScale<Intensity>()

@DimensionClassifier("V") interface Potential
val VOLT = standardScale<Potential>()

@DimensionClassifier("A") interface Current
val AMPERE = standardScale<Current>()

@DimensionClassifier("Ω") interface Resistance
val OHM = standardScale<Resistance>()

@DimensionClassifier("F") interface Capacitance
val FARAD = standardScale<Capacitance>()

@DimensionClassifier("S") interface ElectricalConductance
val SIEMENS = standardScale<ElectricalConductance>()

@DimensionClassifier("C") interface ElectricalCharge
val COULOMB = standardScale<ElectricalCharge>()

@DimensionClassifier("H") interface Inductance
val HENRY = standardScale<Inductance>()

@DimensionClassifier("J/kg") interface MassEnergyDensity
val JOULE_PER_KILOGRAM = standardScale<MassEnergyDensity>()

@DimensionClassifier("J/m³") interface VolumeEnergyDensity
val JOULE_PER_METER3 = standardScale<VolumeEnergyDensity>()

/**
 * Calculates the resistance of a conductor with this electrical resistivity, in the shape of a cylinder of length [L] and cross-sectional area [A].
 * @return The resistance of the conductor.
 * */
@Suppress("LocalVariableName")
fun Quantity<ElectricalResistivity>.cylinderResistance(L: Quantity<Distance>, A: Quantity<Area>) = Quantity((!this * !L) / !A, OHM)

@DimensionClassifier("kg×m²") interface Inertia
val KILOGRAM_METER2 = standardScale<Inertia>()

@DimensionClassifier("Nms") interface ViscousFriction
val NEWTON_METER_SECOND = standardScale<ViscousFriction>()

@DimensionClassifier("rad") interface Angle
val RADIAN = standardScale<Angle>()

@DimensionClassifier("rad/s") interface AngularVelocity
val RADIAN_PER_SECOND = standardScale<AngularVelocity>()

@DimensionClassifier("rad/s²")interface AngularAcceleration
val RADIAN_PER_SECOND2 = standardScale<AngularAcceleration>()

@DimensionClassifier("Nm") interface Torque
val NEWTON_METER = standardScale<Torque>()

@ScaleClassifier("ft·lbf", "ftlbf")
val FOOT_POUND = NEWTON_METER sourceAmplify 1.3558179483314003

@ScaleClassifier("rps")
val REVOLUTION_PER_SECOND = RADIAN_PER_SECOND sourceAmplify 1.0 / 0.1591549430919

@ScaleClassifier("rpm")
val REVOLUTION_PER_MINUTE = RADIAN_PER_SECOND sourceAmplify 1.0 / 9.549296585513721

@DimensionClassifier("K/s") interface TemperatureRate
val KELVIN_PER_SECOND = standardScale<TemperatureRate>()

@DimensionClassifier("Nm/A") interface MotorTorqueConstant
val NEWTON_METER_PER_AMPERE = standardScale<MotorTorqueConstant>()

@DimensionClassifier("V / rad/s") interface MotorBackEmfConstant
val VOLT_PER_RADIAN_PER_SECOND = standardScale<MotorBackEmfConstant>()

@DimensionClassifier("Vs") interface MagneticFlux
val WEBER = standardScale<MagneticFlux>()

inline fun<reified T> Double.classifyAs(scale: SourceQuantityScale<T>) = Quantity(this, scale).classify()

/**
 * Gets a map of dimension type (dimension interface) to a multimap of scale reference (property holding the [QuantityScale]) and its declared [ScaleClassifier]ers.
 * */
val AUXILIARY_CLASSIFIERS: Map<Class<*>, MultiMap<ScaleRef<*>, String>> = run {
    val map = LinkedHashMap<Class<*>, MutableSetMapMultiMap<ScaleRef<*>, String>>()

    SELF.declaredFields.forEach { field ->
        if((QuantityScale::class.java).isAssignableFrom(field.type)) {
            val property = field.kotlinProperty
                ?: return@forEach

            val annotation = property.annotations.firstOrNull { it is ScaleClassifier }
                ?: return@forEach

            annotation as ScaleClassifier

            val scale = checkNotNull(property.getter.call() as? QuantityScale<*>) {
                "Failed to fetch $property"
            }

            val auxiliaries = map.getOrPut(scale.dimensionType) { MutableSetMapMultiMap() }

            val identifiers = if(annotation.identifiers.isEmpty()) {
                listOf(annotation.symbol).iterator()
            }
            else {
                annotation.identifiers.iterator()
            }

            identifiers.forEach {
                @Suppress("UNCHECKED_CAST")
                auxiliaries[property as ScaleRef<*>].add(it)
            }
        }
    }

    map
}

/**
 * Gets a map of dimension type classes (dimension interface).
 * Useful as an ID for config values.
 * Examples values:
 * ```
 * Temperature::class.java [the Class] <-> "Temperature" [the human-readable String]
 * ```
 * */
val DIMENSION_TYPES = run {
    val dimensionTypes = HashSet<Class<*>>()

    SELF.declaredFields.forEach { field ->
        if((QuantityScale::class.java).isAssignableFrom(field.type)) {
            val property = field.kotlinProperty
                ?: return@forEach

            val scale = checkNotNull(property.getter.call() as? QuantityScale<*>) {
                "Failed to fetch $property"
            }

            dimensionTypes.add(scale.dimensionType)
        }
    }

    dimensionTypes
}.associateWithBi { it.sourceName() }

/**
 * Classifies the [value] as a quantity on the scale defined by [dimensionType] (class of the dimension interface).
 * If the dimension interface has [DimensionClassifier], the options from the annotation will be used.
 * Otherwise, [ScaleMultiples.Base1000Standard] will be used and no unit symbol will be added.
 * Examples:
 * ```
 * classify(Temperature::class.java, !Quantity(0.0, CELSIUS)) // 273.15 K
 * ```
 * */
fun classify(dimensionType: Class<*>, value: Double) : String {
    val annotation = dimensionType.getDeclaredAnnotation(DimensionClassifier::class.java)

    return if(annotation == null) {
        classifyIntoMultiple(
            value,
            ScaleMultiples.Base1000Standard.value,
            ScaleMultiples.Base1000Standard.multiples,
            ""
        )
    }
    else {
        classifyIntoMultiple(
            value * annotation.factor,
            annotation.base.value,
            annotation.base.multiples,
            annotation.symbol
        )
    }
}

/**
 * Classifies this quantity into a *multiple* using the [classify], passing in [T] as dimension interface.
 * Examples:
 * ```
 * val quantity = Quantity(10, KILO * METER)
 * quantity.classify() // 10 km
 * ```
 * */
inline fun<reified T> Quantity<T>.classify() = classify(T::class.java, !this)

/**
 * Classifies the [value] as a quantity on the scale defined by [scaleRef] (a defined secondary scale).
 * If the field has [ScaleClassifier], the options from the annotation will be used.
 * Otherwise, as a fallback, the value will be classified using [classify] (classification based on the dimension interface).
 * This normally shouldn't happen if I don't forget to add @Auxiliary to the scales.
 *
 * Examples:
 * ```
 * classifyAuxiliary(::FOOT, !Quantity(10.0, KILO * METER)) // show in feet
 * classifyAuxiliary(::METER, !Quantity(10.0, KILO * METER)) // METER does not have @Auxiliary, so it will just show in.. meters (default scale)
 * classifyAuxiliary(selectedScale, !Quantity(10.0, KILO * METER)) // selectedScale could something from AUXILIARY_CLASSIFIERS
 * ```
 * */
fun classifyAuxiliary(scaleRef: ScaleRef<*>, value: Double) : String {
    val annotation = scaleRef::annotations.get().firstOrNull { it is ScaleClassifier } as? ScaleClassifier
    val quantityScale = scaleRef.call()

    return if(annotation == null) {
        // Fallback - classify in standard scale:
        classify(quantityScale.dimensionType, value)
    }
    else {
        classifyIntoMultiple(
            quantityScale.scale.map(value) * annotation.factor,
            annotation.base.value,
            annotation.base.multiples,
            annotation.symbol
        )
    }
}

/**
 * Classifies this quantity with the specified auxiliary unit.
 * If the auxiliary does not exist, the base classification will be used.
 * Examples:
 * ```
 * val quantity = Quantity(10.0, KILO * METER)
 * quantity.classifyAuxiliary(::FEET) // show in feet
 * quantity.classifyAuxiliary(::METER) // METER does not have @Auxiliary, so it will just show in.. meters (default scale)
 * quantity.classifyAuxiliary(::KELVIN) // Compilation error (type-safe, based on the dimension interface)
 * ```
 * */
inline fun<reified T> Quantity<T>.classifyAuxiliary(scale: ScaleRef<T>) = classifyAuxiliary(scale, !this)
