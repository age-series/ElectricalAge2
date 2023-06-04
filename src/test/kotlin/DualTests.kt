import org.eln2.mc.mathematics.*
import org.eln2.mc.mathematics.sinh
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.*

class DualTests {
    private val EPS = 10e-12

    private fun rangeScanDual(derivatives: Int = 3, start: Double = 0.0, end: Double = 10.0, steps: Int = 10000, action: ((Double, Dual) -> Unit)) {
        rangeScan(start = start, end = end, steps = steps) { x ->
            action(x, Dual.variable(x, derivatives + 1))
        }
    }

    private fun areEqual(a: Double, b: Double) {
        assertEquals(a, b, EPS)
    }

    @Test
    fun sqrtTest() {
        rangeScanDual(start = 1.0) { x, xDual ->
            val v = sqrt(xDual)

            areEqual(v.value, sqrt(x))
            areEqual(v[1], 1.0 / (2.0 * sqrt(x)))
            areEqual(v[2], -1.0 / (4.0 * x.pow(3.0 / 2.0)))
            areEqual(v[3], 3.0 / (8.0 * x.pow(5.0 / 2.0)))
        }
    }

    @Test
    fun sinTest() {
        rangeScanDual { x, xDual ->
            val v = sin(xDual)

            areEqual(v.value, sin(x))
            areEqual(v[1], cos(x))
            areEqual(v[2], -sin(x))
            areEqual(v[3], -cos(x))
        }
    }

    @Test
    fun cosTest() {
        rangeScanDual { x, xDual ->
            val v = cos(xDual)

            areEqual(v.value, cos(x))
            areEqual(v[1], -sin(x))
            areEqual(v[2], -cos(x))
            areEqual(v[3], sin(x))
        }
    }

    @Test
    fun sinhTest() {
        rangeScanDual { x, xDual ->
            val v = sinh(xDual)

            areEqual(v.value, sinh(x))
            areEqual(v[1], cosh(x))
            areEqual(v[2], sinh(x))
            areEqual(v[3], cosh(x))
        }
    }

    @Test
    fun coshTest() {
        rangeScanDual { x, xDual ->
            val v = cosh(xDual)

            areEqual(v.value, cosh(x))
            areEqual(v[1], sinh(x))
            areEqual(v[2], cosh(x))
            areEqual(v[3], sinh(x))
        }
    }

    @Test
    fun powTest() {
        rangeScan(start = 1.0, end = 4.0, steps = 100) { power ->
            rangeScanDual(start = 1.0, steps = 1000) { x, xDual ->
                val v = pow(xDual, power)

                areEqual(v.value, x.pow(power))
                areEqual(v[1], power * x.pow(power - 1))
                areEqual(v[2], (power - 1.0) * power * x.pow(power - 2))
                areEqual(v[3], (power - 2.0) * (power - 1.0) * power * x.pow(power - 3))
            }
        }
    }

    @Test
    fun lnTest() {
        rangeScanDual(start = 5.0, end = 10.0) { x, xDual ->
            val v = ln(xDual)

            areEqual(v.value, ln(x))
            areEqual(v[1], 1.0 / x)
            areEqual(v[2], -1.0 / x.pow(2))
            areEqual(v[3], 2.0 / x.pow(3))
        }
    }
}
