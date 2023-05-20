import org.eln2.mc.mathematics.approxEq
import org.eln2.mc.mathematics.hermiteMappedCubic
import org.junit.jupiter.api.Test

class HermiteSplineTests {
    @Test
    fun testInterpolation() {
        val builder = hermiteMappedCubic()

        val points = ArrayList<Pair<Double, Double>>().also {
            for (i in 0..1000) {
                it.add(Pair(i.toDouble(), i * 10.0))
            }
        }

        points.forEach { builder.point(it.first, it.second) }

        val spline1 = builder.buildHermite()
        val spline2 = builder.buildHermite2()

        points.forEach { (k, v) ->
            assert(spline1.evaluate(k) approxEq v)
            assert(spline1.evaluate(k) == spline2.evaluate(k))
        }
    }
}
