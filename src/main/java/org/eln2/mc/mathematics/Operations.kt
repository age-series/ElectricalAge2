package org.eln2.mc.mathematics

import kotlin.math.abs

/**
 * Compares [this] and [other] for equality using a [tolerance].
 * @return True, if the absolute difference between the two values is smaller than [tolerance]. Otherwise, false.
 * */
fun Double.equals(other: Double, tolerance: Double = 10e-6): Boolean {
    return abs(this - other) < tolerance
}

/**
 * Uses [equals] to compare the two numbers.
 * */
infix fun Double.epsilonEquals(other: Double): Boolean {
    return this.equals(other)
}

/**
 * @return 0 if [this] is NaN. Otherwise, [this].
 * */
fun Double.nanZero(): Double {
    if(this.isNaN()) {
        return 0.0
    }

    return this
}

/**
 * @return 0 if [this] is infinity. Otherwise, [this].
 * */
fun Double.infinityZero(): Double {
    if(this.isInfinite()) {
        return 0.0
    }

    return this
}

/**
 * @return 0 if [this] is NaN or infinity. Otherwise, [this].
 * */
fun Double.definedOrZero(): Double = this.nanZero().infinityZero()
