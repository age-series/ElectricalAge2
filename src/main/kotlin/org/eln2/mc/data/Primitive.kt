package org.eln2.mc.data

@JvmInline
value class IntPair(val value: Long) {
    constructor(first: Int, second: Int) : this((first.toLong() and 0xffffffffL) or (second.toLong() shl 32))

    val first get() = value.toInt()
    val second get() = (value shr 32).toInt()

    operator fun component1() = first
    operator fun component2() = second
    operator fun not() = value

    override fun toString() = "($first, $second)"
}
