import org.eln2.mc.mathematics.*
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sqrt

class GeometryTests {
    private val EPS = 10e-12

    @Test
    fun testVector2d() {
        assert(Vector2d.zero == Vector2d(0.0, 0.0) && Vector2d.zero == Vector2d(0.0))
        assert(Vector2d.one == Vector2d(1.0, 1.0) && Vector2d.one == Vector2d(1.0))
        assert(Vector2d.unitX == Vector2d(1.0, 0.0))
        assert(Vector2d.unitY == Vector2d(0.0, 1.0))
        assert(Vector2d.unitX + Vector2d.unitY == Vector2d.one)
        assert(Vector2d.zero != Vector2d.one)
        assert(Vector2d.zero.length == 0.0 && Vector2d.zero.lengthSqr == 0.0)
        assert(Vector2d.one.length == sqrt(2.0) && Vector2d.one.lengthSqr == 2.0)
        assert(Vector2d.unitX.length == 1.0 && Vector2d.unitX.lengthSqr == 1.0)
        assert(Vector2d.unitY.length == 1.0 && Vector2d.unitY.lengthSqr == 1.0)
        assert((Vector2d.one * 0.5 + Vector2d.one / 2.0) == Vector2d.one)
        assert(Vector2d.one == Vector2d.one * 2.0 - Vector2d.one)
        assert(Vector2d(1000.0, 1000.0).normalized().length.approxEq(1.0))
        assert(Vector2d(1000.0, 1000.0).normalized() * sqrt(1000.0 * 1000.0 * 2) == Vector2d(1000.0, 1000.0))
        assert(lerp(Vector2d.zero, Vector2d(1.0, 2.0), 0.0) == Vector2d.zero)
        assert(lerp(Vector2d.zero, Vector2d(1.0, 2.0), 0.5) == Vector2d(1.0, 2.0) / 2.0)
        assert(lerp(Vector2d.zero, Vector2d(1.0, 2.0), 1.0) == Vector2d(1.0, 2.0))
    }

    @Test
    fun testRotation2d() {
        fun areEqual(vararg values: Rotation2d) {
            require(values.size > 1)

            for (i in 1 until values.size) {
                assert(values[i - 1].approxEq(values[i], EPS))
            }
        }

        val rpi = Rotation2d.exp(PI)

        areEqual(rpi, rpi)
        areEqual(rpi.scaled(1.0), rpi)
        areEqual(rpi.scaled(-1.0), rpi.inverse)
        areEqual(rpi.scaled(0.5), Rotation2d.exp(PI / 2.0))

        areEqual(rpi * rpi, Rotation2d.exp(PI * 2.0), Rotation2d.exp(PI * 4.0), Rotation2d.zero)
        areEqual(rpi * Rotation2d.exp(-PI), Rotation2d.zero)
        areEqual(rpi * rpi.inverse, Rotation2d.zero)

        assert((rpi * Vector2d.unitX).approxEqs(-Vector2d.unitX, EPS))
        assert((Rotation2d.exp(PI * 2.0) * Vector2d.unitX).approxEqs(Vector2d.unitX, EPS))
        assert((Rotation2d.exp(PI * 8.0) * Vector2d.unitX).approxEqs(Vector2d.unitX, EPS))

        areEqual(interpolate(Rotation2d.zero, rpi, 0.0), Rotation2d.zero)
        areEqual(interpolate(Rotation2d.zero, rpi, 1.0), rpi)
        areEqual(interpolate(Rotation2d.zero, rpi, 0.5), Rotation2d.exp(PI / 2.0))
        areEqual(interpolate(Rotation2d.zero, rpi, 0.25), Rotation2d.exp(PI / 4.0))

        rangeScan(start = 0.0, end = 1.0) { t ->
            areEqual(interpolate(rpi, rpi, t), rpi)
        }

        rangeScanRec({ vec ->
            val a = Rotation2d.exp(vec[0])
            val b = Rotation2d.exp(vec[1])

            areEqual(a * (b / a), b)
        }, start = -100.0, end = 100.0, steps = 1000, layers = 2)
    }
}
