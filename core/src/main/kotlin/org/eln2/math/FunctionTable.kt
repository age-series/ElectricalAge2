package org.eln2.math


/**
 * Function Table
 *
 * Defines a function based on the values in a table. The domain of the input array starts from 0 and goes to "xMax"
 * @param point Array of double representing the reference or "y" values.
 * @param xMax The x value that corresponds to the last y value in the point array.
 */
open class FunctionTable(var point: DoubleArray, var xMax: Double, private val mode : ExtrapolationMode) : IFunction {
	var xMaxInv: Double
	var xDelta: Double

	enum class ExtrapolationMode {
		ClosestValue, Linear
	}

	/**
	 * getValue: Given a value, get the corresponding value, linearly fit between two closest points on either side.
	 * Note that this is an interpolation. Extrapolation will return null.
	 * @param x: Given value
	 * @return y: Value for that x, linearly fit between the two closest points on either side if valid; otherwise
	 * 	extrapolate using the method specified when creating this FunctionTable.
	 */
	override fun getValue(x: Double): Double {
		val lx = x.div(xMax)
		return when {
			lx < 0.0 -> { // Extrapolation to the left.
				when (mode) {
					ExtrapolationMode.ClosestValue -> point[0]
					ExtrapolationMode.Linear -> point[0] + (point[1] - point[0]) * (point.size - 1) * lx
				}
			}
			lx > 1.0 -> { // Extrapolation to the right.
				when (mode) {
					ExtrapolationMode.ClosestValue -> point.last()
					ExtrapolationMode.Linear -> point.last() + (point.last() - point[point.size - 2]) * point.last() * (lx - 1.0)
				}
			}
			else -> { // Interpolation
				val sx = lx.times(point.size - 1)
				val idx = sx.toInt()
				point[idx + 1] * sx.rem(1.0) + point[idx] * (1.0f - sx.rem(1.0))
			}
		}
	}

	/*public double getValue(double x) {
		double a = getValueLin(-xDelta);
		double b = getValueLin(xDelta);
		double firFactorA = 0.5, firFactorB = (1 - firFactorA) / 2;
		return getValueLin(x) * firFactorA + getValueLin(x - xDelta) * firFactorB + getValueLin(x + xDelta) * firFactorB;
	}
	*/

	/**
	 * duplicate: Create a copy of this function table with the same mode
	 * @param xFactor Scale x by scalar factor (make wider/thinner)
	 * @param yFactor Scale y by scalar factor (make taller/shorter)
	 * @return New Function Table instance
	 */
	fun duplicate(xFactor: Double, yFactor: Double): FunctionTable {
		val pointCpy = DoubleArray(point.size)
		for (idx in point.indices) {
			pointCpy[idx] = point[idx] * yFactor
		}
		return FunctionTable(pointCpy, xMax * xFactor, mode)
	}

	init {
		xMaxInv = 1.0 / xMax
		xDelta = 1.0 / (point.size - 1) * xMax
	}
}
