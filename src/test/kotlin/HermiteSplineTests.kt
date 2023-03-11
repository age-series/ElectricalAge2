import org.eln2.mc.mathematics.epsilonEquals
import org.eln2.mc.mathematics.mappedHermite
import org.junit.jupiter.api.Test

class HermiteSplineTests {
    @Test
    fun testInterpolation() {
        val builder = mappedHermite()

        val points = ArrayList<Pair<Double, Double>>().also {
            for (i in 0..1000) {
                it.add(Pair(i.toDouble(), i * 10.0))
            }
        }

        points.forEach { builder.point(it.first, it.second) }

        val spline1 = builder.buildHermite()
        val spline2 = builder.buildHermite2()

        points.forEach { (k, v) ->
            assert(spline1.evaluate(k) epsilonEquals v)
            assert(spline1.evaluate(k) == spline2.evaluate(k))
        }
    }
}