package org.eln2.mc.utility

import kotlin.math.abs

fun valueText(value: Double, baseUnit: UnitType): String {
    val valueAbs = abs(value)
    return when {
        valueAbs < 0.0000001 ->
            "0"

        valueAbs < 0.000999 ->
            String.format("%1.2fµ", value * 1000000)

        valueAbs < 0.00999 ->
            String.format("%1.2fm", value * 1000)

        valueAbs < 0.0999 ->
            String.format("%2.1fm", value * 1000)

        valueAbs < 0.999 ->
            String.format("%3.0fm", value * 1000)

        valueAbs < 9.99 ->
            String.format("%1.2f", value)

        valueAbs < 99.9 ->
            String.format("%2.1f", value)

        valueAbs < 999 ->
            String.format("%3.0f", value)

        valueAbs < 9999 ->
            String.format("%1.2fk", value / 1000.0)

        valueAbs < 99999 ->
            String.format("%2.1fk", value / 1000.0)

        else -> // if(value < 1000000)
            String.format("%3.0fk", value / 1000.0)
    } + baseUnit.unit
}
