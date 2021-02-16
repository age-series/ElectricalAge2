package org.eln2.math

import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class Cycle(private val rate: Double) : IFunction {
    override fun getValue(x: Double): Double {
        return sin(this.rate * x)
    }
}

internal class FunctionTest {
    @Test
    fun testInterface() {
        val quarterTurn = Cycle(PI / 2.0)
        for (it in 0 until 20) {
            when (it % 4) {
                0 -> Assertions.assertEquals(quarterTurn.getValue(it.toDouble()), 0.0, 0.000001)
                1 -> Assertions.assertEquals(quarterTurn.getValue(it.toDouble()), 1.0, 0.000001)
                2 -> Assertions.assertEquals(quarterTurn.getValue(it.toDouble()), 0.0, 0.000001)
                3 -> Assertions.assertEquals(quarterTurn.getValue(it.toDouble()), -1.0, 0.000001)
            }
        }
    }

    @Test
    fun testFunctionTable() {
        // A sinusoid can be approximated within 300%
        val errorTolerance = 3.0
        // A sinusoid scaled up by two in both directions can be approximated within 600%
        val errorToleranceAferScale = 6.0

        val doublesRange = (0 until 8).map { cos(it.toDouble().times(PI).div(4.0)) }
        val eigthTurn = FunctionTable(doublesRange.toDoubleArray(), 8.0, FunctionTable.ExtrapolationMode.Linear)
        (-2 until 10).map { it.toDouble().plus(0.5) }.forEach {
            val expected = cos(it.times(PI).div(4.0))
            val actual = eigthTurn.getValue(it)
            Assertions.assertTrue(actual.minus(expected).div(expected).absoluteValue < errorTolerance)
        }
        val eigthTurnScaled = eigthTurn.duplicate(2.0, 2.0)
        (-4 until 20).map { it.toDouble().plus(0.5) }.forEach {
            val expected = cos(it.times(PI).div(2.0))
            val actual = eigthTurnScaled.getValue(it)
            Assertions.assertTrue(actual.minus(expected).div(expected).absoluteValue < errorToleranceAferScale)
        }
    }
}
