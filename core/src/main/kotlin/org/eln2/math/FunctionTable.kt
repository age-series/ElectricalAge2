package org.eln2.math

/**
 * Function Table
 *
 * Defines a function based on the values in a table.
 */
open class FunctionTable(var point: DoubleArray, var xMax: Double) : IFunction {
	var xMaxInv: Double
	var xDelta: Double

	/**
	 * getValue: Given a value, get the corresponding value, linearly fit between two closest points on either side.
	 * @param x: Given value
	 * @return y: Value for that x, linearly fit between the two closest points on either side
	 */
	override fun getValue(x: Double): Double {
		var lx = x
		lx *= xMaxInv
		if (lx < 0f) return point[0] + (point[1] - point[0]) * (point.size - 1) * lx
		if (lx >= 1.0f) return point[point.size - 1] + (point[point.size - 1] - point[point.size - 2]) * (point.size - 1) * (lx - 1.0)
		lx *= point.size - 1.toDouble()
		val idx = lx.toInt()
		lx -= idx.toDouble()
		return point[idx + 1] * lx + point[idx] * (1.0f - lx)
	}

	/*public double getValue(double x) {
		double a = getValueLin(-xDelta);
		double b = getValueLin(xDelta);
		double firFactorA = 0.5, firFactorB = (1 - firFactorA) / 2;
		return getValueLin(x) * firFactorA + getValueLin(x - xDelta) * firFactorB + getValueLin(x + xDelta) * firFactorB;
	}
	*/

	/**
	 * duplicate: Create a copy of this function table
	 * @param xFactor Scale x by scalar factor (make wider/thinner)
	 * @param yFactor Scale y by scalar factor (make taller/shorter)
	 * @return New Function Table instance
	 */
	fun duplicate(xFactor: Double, yFactor: Double): FunctionTable {
		val pointCpy = DoubleArray(point.size)
		for (idx in point.indices) {
			pointCpy[idx] = point[idx] * yFactor
		}
		return FunctionTable(pointCpy, xMax * xFactor)
	}

	init {
		xMaxInv = 1.0 / xMax
		xDelta = 1.0 / (point.size - 1) * xMax
	}
}
