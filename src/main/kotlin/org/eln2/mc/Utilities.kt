package org.eln2.mc

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemUtils
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.server.ServerLifecycleHooks
import org.ageseries.libage.data.*
import org.eln2.mc.data.CsvLoader
import org.eln2.mc.mathematics.*
import org.joml.Vector3f
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.system.measureNanoTime

fun randomFloat(min: Float, max: Float) = map(Random.nextFloat(), 0f, 1f, min, max)

fun <T> all(vararg items: T, condition: (T) -> Boolean) = items.asList().all(condition)

@OptIn(ExperimentalContracts::class)
fun measureDuration(block: () -> Unit) : Quantity<Time> {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    val result = measureNanoTime(block)

    return Quantity(result.toDouble(), NANOSECONDS)
}

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

fun prepareBucket(
    bucketStack: ItemStack,
    target: Item,
    player: Player,
    hand: InteractionHand,
    pos: BlockPos,
    sound: SoundEvent = SoundEvents.BUCKET_FILL,
    source: SoundSource = SoundSource.BLOCKS,
) {
    player.setItemInHand(
        hand,
        ItemUtils.createFilledResult(
            bucketStack,
            player,
            ItemStack(target, 1)
        )
    )

    player.level.playSound(null, pos, sound, source, 1.0F, 1.0F);
}

inline fun requireIsOnServerThread(message: () -> String) = require(ServerLifecycleHooks.getCurrentServer().isSameThread) {
    message()
}
fun requireIsOnServerThread() = requireIsOnServerThread { "Requirement failed: not on server thread (${Thread.currentThread()})" }

inline fun requireIsOnRenderThread(message: () -> String) = require(RenderSystem.isOnRenderThread(), message)

fun requireIsOnRenderThread() = requireIsOnRenderThread { "Requirement failed: not on render thread (${Thread.currentThread()})" }

fun<K, V> ConcurrentHashMap<K, V>.atomicRemoveIf(consumer: (Map.Entry<K, V>) -> Boolean) {
    this.entries.forEach { entry ->
        if(consumer(entry)) {
            this.remove(entry.key, entry.value)
        }
    }
}

private fun validateSide(side: Dist) = when(side) {
    Dist.CLIENT -> requireIsOnRenderThread {
        "Accessed client only"
    }
    Dist.DEDICATED_SERVER -> requireIsOnServerThread {
        "Accessed server only"
    }
}
class SidedLazy<T>(factory: () -> T, val side: Dist) {
    private val lazy = lazy(factory)

    fun get() : T {
        validateSide(side)
        return lazy.value
    }

    operator fun invoke() = get()
}

class SidedHolder<T>(initialValue: T, val side: Dist) {
    var value = initialValue
        get() {
            validateSide(side)
            return field
        }
        set(value) {
            validateSide(side)
            field = value
        }
}

fun<T> clientOnlyHolder(factory: () -> T) = SidedLazy(factory, Dist.CLIENT)
fun<T> serverOnlyHolder(factory: () -> T) = SidedLazy(factory, Dist.DEDICATED_SERVER)
fun<T> clientOnlyField(value: T) = SidedHolder(value, Dist.CLIENT)
fun<T> serverOnlyField(value: T) = SidedHolder(value, Dist.DEDICATED_SERVER)

private val UNIQUE_ID_ATOMIC = AtomicInteger()

fun getUniqueId() = UNIQUE_ID_ATOMIC.getAndIncrement()

inline fun<T> T?.requireNotNull(message: () -> String) : T {
    require(this != null, message)
    return this
}

enum class WeatherStateType {
    Clear,
    Rain,
    Thunder
}

enum class DayNightStateType {
    Day,
    Night
}

fun Level.getWeatherStateType() =
    if(this.isThundering) {
        WeatherStateType.Thunder
    } else if(this.isRaining) {
        WeatherStateType.Rain
    } else {
        WeatherStateType.Clear
    }

fun Level.getDayNightStateType() =
    if(this.isDay) {
        DayNightStateType.Day
    } else {
        DayNightStateType.Night
    }
