package org.eln2.debug

import com.andreapivetta.kolor.Color
import com.andreapivetta.kolor.Kolor
import org.apache.commons.math3.linear.RealMatrix
import org.apache.commons.math3.linear.RealVector
import org.eln2.sim.electrical.mna.Circuit

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

fun mnaFormatNoUnknowns(matrix: RealMatrix, knowns: RealVector, color: Boolean = true): String {
    val mftLines = matrixFormat(matrix, false).split("\n")
    val kftLines = knownsFormat(knowns, false).split("\n")
    val biggest = kotlin.math.max(mftLines.size, kftLines.size)
    var output = ""

    (0 until biggest).withIndex().forEach { entry ->
        if (color) {
            output +=Kolor.foreground(mftLines[entry.value], Color.RED)
            output += if (entry.index == biggest - 2) " x = " else "     "
            output += Kolor.foreground(kftLines[entry.value], Color.BLUE)
        }else{
            output +=mftLines[entry.value]
            output += if (entry.index == biggest - 2) " x = " else "     "
            output += " "
            output += kftLines[entry.value]
        }
        output += "\n"
    }
    return output
}

fun mnaFormat(circuit: Circuit, color: Boolean = true): String {
    val localMatrix = circuit.matrix
    val localKnowns = circuit.knowns
    if ((localMatrix == null) or (localKnowns == null)) return ""
    return mnaFormat(localMatrix!!, localKnowns!!, circuit.unknowns, color)
}

fun mnaFormat(matrix: RealMatrix, knowns: RealVector, unknowns: RealVector?, color: Boolean = true): String {
    if (unknowns == null) return mnaFormatNoUnknowns(matrix, knowns, color)
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

@Suppress("UNUSED_PARAMETER") // Remove this line when implementation created.
fun mnaIntelligentFormat(circuit: Circuit): String {
    // TODO: https://github.com/eln2/eln2/issues/88

    // Use https://lpsa.swarthmore.edu/Systems/Electrical/mna/MNA3.html as a guide.
    // Please color the G matrix red. Every other matrix can be a color of your choosing, but please expose every internal matrix.
    // (so, G, B, C, D, v, j, i, e)
    // Tip: use Kolor.foreground("mytext", Color.RED) to color text. Avoid background colors and hard to see foreground colors.
    return ""
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
fun mnaPrint(matrix: RealMatrix, knowns: RealVector, unknowns: RealVector?, color: Boolean = true) {
    println(mnaFormat(matrix, knowns, unknowns, color))
}

@Suppress("unused")
fun mnaPrint(circuit: Circuit, color: Boolean = true) {
    println(mnaFormat(circuit, color))
}

@Suppress("unused")
fun mnaIntelligentPrint(circuit: Circuit) {
    println(mnaIntelligentFormat(circuit))
}
