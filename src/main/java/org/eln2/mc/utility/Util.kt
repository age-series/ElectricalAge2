package org.eln2.mc.utility

import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.state.BlockState
import org.ageseries.libage.data.MultiMap
import org.ageseries.libage.data.MutableSetMapMultiMap
import org.ageseries.libage.data.mutableMultiMapOf
import org.eln2.mc.common.content.FermentationBarrelBlockEntity
import org.eln2.mc.data.NANOSECONDS
import org.eln2.mc.data.Quantity
import org.eln2.mc.toBlockPos
import kotlin.system.measureNanoTime

fun<T> all(vararg items: T, condition: (T) -> Boolean) = items.asList().all(condition)

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
    if(c.isDigit) return c
    if(c.isSubscriptDigit) return subscriptToDigit(c)
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
fun <T : BlockEntity?, U> ticker(f: (pLevel: Level, pPos: BlockPos, pState: BlockState, pBlockEntity: U) -> Unit) = BlockEntityTicker<T> { level, pos, state, e ->
    f(level, pos, state, e as U)
}
