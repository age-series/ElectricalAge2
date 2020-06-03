package org.eln2.debug

import com.andreapivetta.kolor.Color
import com.andreapivetta.kolor.Kolor
import org.apache.commons.math3.linear.RealMatrix
import org.apache.commons.math3.linear.RealVector

const val FORMAT_SIZE = 8

fun matrixFormat(matrix: RealMatrix, headerfooter: Boolean = true): String {
    val matrixData = matrix.data
    if (matrixData.isEmpty()) return ""
    val singleMatrix = matrixData[0].size <= 1
    var output = ""

    if (headerfooter)
    output += "== Begin MNA Matrix ==\n"

    matrixData.withIndex().forEach {
        output += if (!singleMatrix) {
            when (it.index) {
                0 -> {
                    "┌ "
                }
                matrixData.size - 1 -> {
                    "└ "
                }
                else -> {
                    "│ "
                }
            }
        }else {
            "[ "
        }

        it.value.forEach { entry -> output += ("${entry.toString().format(FORMAT_SIZE)}, ") }

        output += if (!singleMatrix) {
            when (it.index) {
                0 -> {
                    " ┐"
                }
                matrixData.size - 1 -> {
                    " ┘"
                }
                else -> {
                    " │"
                }
            }
        }else{
            " ]"
        }
        output += "\n"
    }

    if (headerfooter) output += "== End MNA Matrix ===\n"
    return output
}

fun knownsFormat(knowns: RealVector, headerfooter: Boolean = true): String {
    val knownsData = knowns.toArray()
    if (knownsData.isEmpty()) return ""
    val singleKnown = knownsData.size <= 1
    var output = ""

    if (headerfooter)
        output += "== Begin MNA Matrix ==\n"

    knownsData.withIndex().forEach {
        output += if (!singleKnown) {
            when (it.index) {
                0 -> {
                    "┌ "
                }
                knownsData.size - 1 -> {
                    "└ "
                }
                else -> {
                    "│ "
                }
            }
        }else {
            "[ "
        }

        output += it.value.toString().format(FORMAT_SIZE)


        output += if (!singleKnown) {
            when (it.index) {
                0 -> {
                    " ┐"
                }
                knownsData.size - 1 -> {
                    " ┘"
                }
                else -> {
                    " │"
                }
            }
        }else{
            " ]"
        }
        output += "\n"
    }

    if (headerfooter) output += "== End Knowns Matrix ===\n"
    return output
}

fun unknownsFormat(unknowns: RealVector?, headerfooter: Boolean = true): String {
    if (unknowns == null) return ""
    val unknownsData = unknowns.toArray()
    if (unknownsData.isEmpty()) return ""
    val singleKnown = unknownsData.size <= 1
    var output = ""

    if (headerfooter)
        output += "== Begin MNA Matrix ==\n"

    unknownsData.withIndex().forEach {
        output += if (!singleKnown) {
            when (it.index) {
                0 -> {
                    "┌ "
                }
                unknownsData.size - 1 -> {
                    "└ "
                }
                else -> {
                    "│ "
                }
            }
        }else {
            "[ "
        }

        output += it.value.toString().format(FORMAT_SIZE)


        output += if (!singleKnown) {
            when (it.index) {
                0 -> {
                    " ┐"
                }
                unknownsData.size - 1 -> {
                    " ┘"
                }
                else -> {
                    " │"
                }
            }
        }else{
            " ]"
        }
        output += "\n"
    }

    if (headerfooter) output += "== End Unknowns Matrix ===\n"
    return output
}

fun mnaFormat(matrix: RealMatrix, knowns: RealVector, color: Boolean = true): String {
    val mftLines = matrixFormat(matrix, false).split("\n")
    val kftLines = knownsFormat(knowns, false).split("\n")
    val biggest = kotlin.math.max(mftLines.size, kftLines.size)
    var output = ""

    (0 until biggest).withIndex().forEach { entry ->
        if (color) {
            output +=Kolor.foreground(mftLines[entry.value], Color.RED)
            if (entry.index == biggest - 2) output += " x = " else output += "     "
            output += Kolor.foreground(kftLines[entry.value], Color.BLUE)
        }else{
            output +=mftLines[entry.value]
            if (entry.index == biggest - 2) output += " x = " else output += "     "
            output += " "
            output += kftLines[entry.value]
        }
        output += "\n"
    }
    return output
}

fun mnaFormatAll(matrix: RealMatrix, knowns: RealVector, unknowns: RealVector?, color: Boolean = true): String {
    if (unknowns == null) return mnaFormat(matrix, knowns, color)
    val mftLines = matrixFormat(matrix, false).split("\n")
    val kftLines = knownsFormat(knowns, false).split("\n")
    val uftLines = unknownsFormat(unknowns, false).split("\n")
    val biggest = kotlin.math.max(mftLines.size, kotlin.math.max(kftLines.size, uftLines.size))
    var output = ""

    (0 until biggest).withIndex().forEach { entry ->
        if (color) {
            output +=Kolor.foreground(mftLines[entry.value], Color.RED)
            output += " "
            output += Kolor.foreground(uftLines[entry.value], Color.GREEN)
            output += if (entry.index == biggest - 2) " = " else "   "
            output += Kolor.foreground(kftLines[entry.value], Color.BLUE)
        }else{
            output +=mftLines[entry.value]
            output += " "
            output += uftLines[entry.value]
            output += if (entry.index == biggest - 2) " = " else "   "
            output += kftLines[entry.value]
        }
        output += "\n"
    }
    return output
}

@Suppress("unused")
fun matrixPrint(matrix: RealMatrix) {
    println(matrixFormat(matrix, true))
}

@Suppress("unused")
fun knownsPrint(knowns: RealVector) {
    println(knownsFormat(knowns, true))
}

@Suppress("unused")
fun unknownsPrint(unknowns: RealVector?) {
    if (unknowns != null) println(unknownsFormat(unknowns, true))
}

@Suppress("unused")
fun mnaPrint(matrix: RealMatrix, knowns: RealVector, color: Boolean = true) {
    println(mnaFormat(matrix, knowns, color))
}

@Suppress("unused")
fun mnaPrintAll(matrix: RealMatrix, knowns: RealVector, unknowns: RealVector?, color: Boolean = true) {
    println(mnaFormatAll(matrix, knowns, unknowns, color))
}
