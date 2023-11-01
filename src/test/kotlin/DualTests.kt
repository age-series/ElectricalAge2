import org.eln2.mc.mathematics.*
import org.eln2.mc.mathematics.sinh
import org.junit.jupiter.api.Test
import kotlin.math.*
import kotlin.random.Random

private const val EPS = 1e-8

class DualTests {
    private val random = Random(3141)

    private fun range(derivatives: Int = 3, start: Double = 0.0, end: Double = 10.0, steps: Int = 10000, action: ((Double, Dual) -> Unit)) {
        rangeScan(start = start, end = end, steps = steps) { x ->
            action(x, Dual.variable(x, derivatives + 1))
        }
    }

    private fun areEqual(vararg n : Double) {
        for (i in 1 until n.size) {
            assert(n[i - 1].approxEq(n[i], EPS))
        }
    }


    private fun areEqual(vararg n : Dual) {
        for (i in 1 until n.size) {
            assert(n[i - 1].approxEq(n[i], EPS))
        }
    }

    private fun rngNz() = random.nextDouble(0.5, 10.0) * snz(random.nextDouble(-1.0, 1.0))

    @Test
    fun sqrtTest() {
        range(start = 1.0) { x, xDual ->
            val v = sqrt(xDual)

            areEqual(v.value, sqrt(x))
            areEqual(v[1], 1.0 / (2.0 * sqrt(x)))
            areEqual(v[2], -1.0 / (4.0 * x.pow(3.0 / 2.0)))
            areEqual(v[3], 3.0 / (8.0 * x.pow(5.0 / 2.0)))
        }
    }

    @Test
    fun sinTest() {
        range { x, xDual ->
            val v = sin(xDual)

            areEqual(v.value, sin(x))
            areEqual(v[1], cos(x))
            areEqual(v[2], -sin(x))
            areEqual(v[3], -cos(x))
        }
    }

    @Test
    fun cosTest() {
        range { x, xDual ->
            val v = cos(xDual)

            areEqual(v.value, cos(x))
            areEqual(v[1], -sin(x))
            areEqual(v[2], -cos(x))
            areEqual(v[3], sin(x))
        }
    }

    @Test
    fun sinhTest() {
        range { x, xDual ->
            val v = sinh(xDual)

            areEqual(v.value, sinh(x))
            areEqual(v[1], cosh(x))
            areEqual(v[2], sinh(x))
            areEqual(v[3], cosh(x))
        }
    }

    @Test
    fun coshTest() {
        range { x, xDual ->
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
            range(start = 1.0, steps = 1000) { x, xDual ->
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
        range(start = 5.0, end = 10.0) { x, xDual ->
            val v = ln(xDual)

            areEqual(v.value, ln(x))
            areEqual(v[1], 1.0 / x)
            areEqual(v[2], -1.0 / x.pow(2))
            areEqual(v[3], 2.0 / x.pow(3))
        }
    }

    @Test
    fun constTest() {
        repeat(100000) {
            val x = Dual.of(rngNz(), rngNz(), rngNz(), rngNz())
            val c = rngNz()
            val cDual = Dual.const(c, x.size)

            areEqual(
                x + c,
                c + x,
                x + cDual,
                cDual + x
            )

            areEqual(
                x - c,
                x - cDual
            )

            areEqual(
                c - x,
                cDual - x
            )

            areEqual(
                x * c,
                c * x,
                x * cDual,
                cDual * x
            )

            areEqual(
                x / c,
                x / cDual
            )

            areEqual(
                c / x,
                cDual / x,
            )
        }
    }
}
