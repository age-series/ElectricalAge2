import org.eln2.mc.mathematics.InterpolatorBuilder
import org.eln2.mc.mathematics.approxEq
import org.junit.jupiter.api.Test

class HermiteSplineTests {
    @Test
    fun testInterpolation() {
        val builder = InterpolatorBuilder()

        val points = ArrayList<Pair<Double, Double>>().also {
            for (i in 0..1000) {
                it.add(Pair(i.toDouble(), i * 10.0))
            }
        }

        points.forEach { builder.with(it.first, it.second) }

        val spline1 = builder.buildCubicKB()

        points.forEach { (k, v) ->
            assert(spline1.evaluate(k) approxEq v)
        }
    }
}
