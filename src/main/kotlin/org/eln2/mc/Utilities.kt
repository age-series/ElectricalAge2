package org.eln2.mc

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.ageseries.libage.data.MultiMap
import org.ageseries.libage.data.MutableSetMapMultiMap
import org.eln2.mc.data.CsvLoader
import org.eln2.mc.data.NANOSECONDS
import org.eln2.mc.data.Quantity
import org.eln2.mc.data.Time
import org.eln2.mc.mathematics.*
import org.joml.Vector3f
import kotlin.math.max
import kotlin.math.min
import kotlin.system.measureNanoTime

fun <T> all(vararg items: T, condition: (T) -> Boolean) = items.asList().all(condition)

fun measureDuration(block: () -> Unit) = Quantity(
    measureNanoTime(block).toDouble(), NANOSECONDS
)

val digitRange = '0'..'9'
val subscriptDigitRange = '₀'..'₉'

operator fun CharRange.get(x: Int) = this.elementAt(x)

val Char.isLetter get() = this in 'a'..'z' || this in 'A'..'Z'
val Char.isDigit get() = this in digitRange
val Char.isSubscriptDigit get() = this in subscriptDigitRange
val Char.isDigitOrSubscriptDigit get() = this.isDigit || this.isSubscriptDigit

fun subscriptToDigit(c: Char): Char {
    require(c.isSubscriptDigit) { "$c is not a subscript digit" }
    return digitRange[subscriptDigitRange.indexOf(c)]
}

fun digitToSubscript(c: Char): Char {
    require(c.isDigit) { "$c is not a digit" }
    return subscriptDigitRange[digitRange.indexOf(c)]
}

fun Int.toStringSubscript() = String(this.toString().map { digitToSubscript(it) }.toCharArray())

fun charDigitValue(c: Char): Char {
    if (c.isDigit) return c
    if (c.isSubscriptDigit) return subscriptToDigit(c)
    error("$c is not a digit or subscript digit")
}

inline fun <T, K> List<T>.associateByMulti(keySelector: (T) -> K): MultiMap<K, T> {
    val result = MutableSetMapMultiMap<K, T>()
    this.forEach { value -> result[keySelector(value)].add(value) }
    return result
}

inline fun <T, K> Array<T>.associateByMulti(keySelector: (T) -> K): MultiMap<K, T> {
    val result = MutableSetMapMultiMap<K, T>()
    this.forEach { value -> result[keySelector(value)].add(value) }
    return result
}


inline fun <T, K> Set<T>.associateByMulti(keySelector: (T) -> K): MultiMap<K, T> {
    val result = MutableSetMapMultiMap<K, T>()
    this.forEach { value -> result[keySelector(value)].add(value) }
    return result
}


inline fun <T, K> Collection<T>.associateByMulti(keySelector: (T) -> K): MultiMap<K, T> {
    val result = MutableSetMapMultiMap<K, T>()
    this.forEach { value -> result[keySelector(value)].add(value) }
    return result
}

@Suppress("UNCHECKED_CAST")
fun <T : BlockEntity?, U> ticker(f: (pLevel: Level, pPos: BlockPos, pState: BlockState, pBlockEntity: U) -> Unit) =
    BlockEntityTicker<T> { level, pos, state, e ->
        f(level, pos, state, e as U)
    }

fun <T> clipScene(entity: LivingEntity, access: ((T) -> AABB), objects: Collection<T>): T? {
    val intersections = LinkedHashMap<Vec3, T>()

    val eyePos = Vec3(entity.x, entity.eyeY, entity.z)

    objects.forEach { obj ->
        val box = access(obj)

        val intersection = box.viewClip(entity)

        if (!intersection.isEmpty) {
            intersections[intersection.get()] = obj
        }
    }

    val entry = intersections.minByOrNull { entry ->
        (eyePos - entry.key).length()
    }

    return entry?.value
}

fun componentMin(a: Vector3f, b: Vector3f): Vector3f {
    return Vector3f(
        min(a.x(), b.x()),
        min(a.y(), b.y()),
        min(a.z(), b.z())
    )
}

fun componentMax(a: Vector3f, b: Vector3f): Vector3f {
    return Vector3f(
        max(a.x(), b.x()),
        max(a.y(), b.y()),
        max(a.z(), b.z())
    )
}

class Stopwatch {
    private var initialTimeStamp = System.nanoTime()
    private var lastTimeStamp = initialTimeStamp

    fun sample(): Quantity<Time> {
        val current = System.nanoTime()
        val elapsedNanoseconds = current - lastTimeStamp
        lastTimeStamp = current

        return Quantity(elapsedNanoseconds.toDouble(), NANOSECONDS)
    }

    val total get() = Quantity((System.nanoTime() - initialTimeStamp).toDouble(), NANOSECONDS)

    fun resetTotal() {
        initialTimeStamp = System.nanoTime()
    }
}

fun readPairs(name: String): List<Pair<String, String>> = readDatasetString(name)
    .lines().filter { it.isNotBlank() }.map { line ->
        line.split("\\s".toRegex()).toTypedArray().let {
            require(it.size == 2) {
                "KVP \"$line\" mapped to ${it.joinToString(" ")}"
            }

            Pair(it[0], it[1])
        }
    }

fun loadPairInterpolator(
    name: String,
    mergeDuplicates: Boolean = true,
    t: Double = 0.0,
    b: Double = 0.0,
    c: Double = 0.0,
) = InterpolatorBuilder().let { sb ->
    readPairs(name).map { (kStr, vStr) ->
        Pair(
            kStr.toDoubleOrNull() ?: error("Failed to parse key \"$kStr\""),
            vStr.toDoubleOrNull() ?: error("Failed to parse value \"$vStr\"")
        )
    }.let {
        if (mergeDuplicates) {
            val buckets = ArrayList<Pair<Double, ArrayList<Double>>>()

            it.forEach { (k, v) ->
                fun create() = buckets.add(Pair(k, arrayListOf(v)))

                if (buckets.isEmpty()) {
                    create()
                } else {
                    val (lastKey, lastBucket) = buckets.last()

                    if (lastKey == k) lastBucket.add(v)
                    else create()
                }
            }

            val results = ArrayList<Pair<Double, Double>>()

            buckets.forEach { (key, values) ->
                results.add(
                    Pair(
                        key,
                        values.sum() / values.size
                    )
                )
            }

            results
        } else {
            it
        }
    }.forEach { (k, v) -> sb.with(k, v) }

    sb.buildCubic(t, b, c)
}

fun readDatasetString(name: String) = getResourceString(resource("datasets/$name"))
fun readCsvNumbers(name: String) = CsvLoader.loadNumericData(readDatasetString(name))
fun loadCsvSpline(name: String, keyIndex: Int, valueIndex: Int): Spline1d {
    val builder = InterpolatorBuilder()

    readCsvNumbers(name).also { csv ->
        csv.entries.forEach {
            builder.with(it[keyIndex], it[valueIndex])
        }
    }

    return builder.buildCubic()
}

fun loadCsvGrid2(name: String): MappedGridInterpolator {
    val csv = readCsvNumbers(name)

    var xSize = 0
    var ySize = 0

    val xMapping = InterpolatorBuilder().apply {
        csv.headers.drop(1).forEach { header ->
            with(header.toDouble(), (xSize++).toDouble())
        }
    }.buildCubic()

    val yMapping = InterpolatorBuilder().apply {
        csv.entries.forEach {
            with(it[0], (ySize++).toDouble())
        }
    }.buildCubic()

    val grid = arrayKDGridDOf(xSize, ySize)

    for (y in 0 until ySize) {
        val row = csv.entries[y]

        row.values.drop(1).forEachIndexed { x, d ->
            grid[x, y] = d
        }
    }

    return MappedGridInterpolator(grid.interpolator(), listOf(xMapping, yMapping))
}
