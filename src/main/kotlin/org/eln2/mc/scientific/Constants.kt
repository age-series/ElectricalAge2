package org.eln2.mc.scientific

import java.math.BigDecimal
import java.math.MathContext

val CONST_CONTEXT = MathContext(64)

val C = 299792458.0
val C2 = 8.9875518e+16

val EPS0_B = BigDecimal("1.25663706212").multiply(BigDecimal("1e-6"), CONST_CONTEXT)
val E_B = BigDecimal("1.602176634").multiply(BigDecimal("1e−19"), CONST_CONTEXT)
val C_B = BigDecimal("299792458")
val Me_B = BigDecimal("9.1093837015").multiply(BigDecimal("1e-31"), CONST_CONTEXT)
val PI_B = BigDecimal("3.141592653589793238462643383279")
val NA_B = BigDecimal("6.02214076").multiply(BigDecimal("1e23"), CONST_CONTEXT)
val MU_B = BigDecimal("0.99999999965").multiply(BigDecimal("1e-3"), CONST_CONTEXT)
