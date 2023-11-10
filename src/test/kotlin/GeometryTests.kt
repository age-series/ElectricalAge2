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
        assert(Vector2d.zero.norm == 0.0 && Vector2d.zero.normSqr == 0.0)
        assert(Vector2d.one.norm == sqrt(2.0) && Vector2d.one.normSqr == 2.0)
        assert(Vector2d.unitX.norm == 1.0 && Vector2d.unitX.normSqr == 1.0)
        assert(Vector2d.unitY.norm == 1.0 && Vector2d.unitY.normSqr == 1.0)
        assert((Vector2d.one * 0.5 + Vector2d.one / 2.0) == Vector2d.one)
        assert(Vector2d.one == Vector2d.one * 2.0 - Vector2d.one)
        assert(Vector2d(1000.0, 1000.0).normalized().norm.approxEq(1.0))
        assert(Vector2d(1000.0, 1000.0).normalized() * sqrt(1000.0 * 1000.0 * 2) == Vector2d(1000.0, 1000.0))
        assert(Vector2d.lerp(Vector2d.zero, Vector2d(1.0, 2.0), 0.0) == Vector2d.zero)
        assert(Vector2d.lerp(Vector2d.zero, Vector2d(1.0, 2.0), 0.5) == Vector2d(1.0, 2.0) / 2.0)
        assert(Vector2d.lerp(Vector2d.zero, Vector2d(1.0, 2.0), 1.0) == Vector2d(1.0, 2.0))
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

        areEqual(rpi * rpi, Rotation2d.exp(PI * 2.0), Rotation2d.exp(PI * 4.0), Rotation2d.identity)
        areEqual(rpi * Rotation2d.exp(-PI), Rotation2d.identity)
        areEqual(rpi * rpi.inverse, Rotation2d.identity)

        assert((rpi * Vector2d.unitX).approxEq(-Vector2d.unitX, EPS))
        assert((Rotation2d.exp(PI * 2.0) * Vector2d.unitX).approxEq(Vector2d.unitX, EPS))
        assert((Rotation2d.exp(PI * 8.0) * Vector2d.unitX).approxEq(Vector2d.unitX, EPS))

        areEqual(Rotation2d.interpolate(Rotation2d.identity, rpi, 0.0), Rotation2d.identity)
        areEqual(Rotation2d.interpolate(Rotation2d.identity, rpi, 1.0), rpi)
        areEqual(Rotation2d.interpolate(Rotation2d.identity, rpi, 0.5), Rotation2d.exp(PI / 2.0))
        areEqual(Rotation2d.interpolate(Rotation2d.identity, rpi, 0.25), Rotation2d.exp(PI / 4.0))

        rangeScan(start = 0.0, end = 1.0) { t ->
            areEqual(Rotation2d.interpolate(rpi, rpi, t), rpi)
        }

        rangeScanRec({ vec ->
            val a = Rotation2d.exp(vec[0])
            val b = Rotation2d.exp(vec[1])

            areEqual(a * (b / a), b)
        }, start = -100.0, end = 100.0, steps = 1000, layers = 2)
    }
}
