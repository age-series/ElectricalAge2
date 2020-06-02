package org.eln2.math

/**
 * IFunction
 *
 * Interface for any function with one variable in, and one variable out (eg, linear functions, piecewise, log, exp,..)
 */
interface IFunction {
    /**
     * getValue
     * @param x The input variable
     * @return The output variable
     */
    fun getValue(x: Double): Double
}
