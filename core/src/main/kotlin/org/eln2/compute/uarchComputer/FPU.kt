package org.eln2.compute.uarchComputer

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Floating Point Unit for uarchComputer
 */
@ExperimentalUnsignedTypes
class FPU {
    var inputA = 0.0
    var inputB = 0.0

    var inputAL: UInt
        get() {
            return inputA.toRawBits().toUInt()
        }
        set(value) {
            inputA = Double.Companion.fromBits(
                inputA.toRawBits().toULong().and(0xFFFFFFFF00000000u).or(value.toULong()).toLong()
            )
        }

    var inputAH: UInt
        get() {
            return inputA.toRawBits().toULong().shr(32).toUInt()
        }
        set(value) {
            inputA = Double.Companion.fromBits(
                inputA.toRawBits().toULong().and(0xFFFFFFFFu).or(value.toULong().shl(32)).toLong()
            )
        }

    var inputBL: UInt
        get() {
            return inputB.toRawBits().toUInt()
        }
        set(value) {
            inputB = Double.Companion.fromBits(
                inputB.toRawBits().toULong().and(0xFFFFFFFF00000000u).or(value.toULong()).toLong()
            )
        }

    var inputBH: UInt
        get() {
            return inputB.toRawBits().toULong().shr(32).toUInt()
        }
        set(value) {
            inputB = Double.Companion.fromBits(
                inputB.toRawBits().toULong().and(0xFFFFFFFFu).or(value.toULong().shl(32)).toLong()
            )
        }

    /**
     * FPU Operations List
     */
    enum class FPUOps { Add, Cos, Div, Exp, Inv, Int, Ln, MinusOne, Mult, Neg, One, Sqre, Sqrt, Sub, ToDouble, Zero }

    var currOp = FPUOps.Zero

    /**
     * step - does computations on the registers. Load your data in before this, and grab your data after this.
     */
    fun step() {
        when (currOp) {
            FPUOps.Add -> inputA += inputB
            FPUOps.Cos -> inputA = cos(inputA)
            FPUOps.Div -> inputA /= inputB
            FPUOps.Exp -> inputA = exp(inputA)
            FPUOps.Inv -> inputA = 1 / inputA
            FPUOps.Int -> {
                val tmp = inputA.toLong()
                inputAH = tmp.toULong().shr(32).toUInt()
                inputAL = tmp.toUInt()
            }
            FPUOps.Ln -> inputA = ln(inputA)
            FPUOps.MinusOne -> inputA = -1.0
            FPUOps.Mult -> inputA *= inputB
            FPUOps.Neg -> inputA *= -1.0
            FPUOps.One -> inputA = 1.0
            FPUOps.Sqre -> inputA *= inputA
            FPUOps.Sqrt -> inputA = sqrt(inputA)
            FPUOps.Sub -> inputA -= inputB
            FPUOps.ToDouble -> inputA = inputA.toRawBits().toDouble()
            FPUOps.Zero -> inputA = 0.0
        }
    }
}
