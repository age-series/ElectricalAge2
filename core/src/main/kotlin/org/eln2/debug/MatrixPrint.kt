package org.eln2.debug

import org.apache.commons.math3.linear.MatrixUtils
import org.apache.commons.math3.linear.RealMatrix
import org.apache.commons.math3.linear.RealVector
import org.eln2.sim.electrical.mna.Circuit

/**
 * matrixFormat: Prints out the A matrix for the MNA
 *
 * @param matrix The A matrix
 * @param headerfooter If you want a footer
 * @return matrix output
 */
fun matrixFormat(matrix: RealMatrix?, headerfooter: Boolean = true): String {
    if(matrix == null || matrix.data.isEmpty()) return ""
    val rows = matrix.rowDimension
    val columns = matrix.columnDimension
    val singleRow = rows <= 1
    val sb = StringBuilder()

    if (headerfooter) sb.append("== Begin MNA Matrix ==\n")
    
    val columnWidths = (0 until columns).map {
        matrix.getColumn(it).map { entry -> entry.toString().length }.maxOrNull() ?: 0
    }.toList()

    for(row in 0 until rows) {
        sb.append(if (!singleRow) {
            when (row) {
                0 -> {
                    "┌"
                }
                rows - 1 -> {
                    "└"
                }
                else -> {
                    "│"
                }
            }
        } else {
            "["
        })

        matrix.getRow(row).withIndex().forEach { (index, entry) -> sb.append(" %${columnWidths[index]}s ".format(entry.toString())) }

        sb.append(if (!singleRow) {
            when (row) {
                0 -> {
                    "┐"
                }
                rows- 1 -> {
                    "┘"
                }
                else -> {
                    "│"
                }
            }
        }else{
            "]"
        })
        sb.append("\n")
    }

    if (headerfooter) sb.append("== End MNA Matrix ===\n")
    return sb.toString()
}

/**
 * Format a column vector into a string.
 */
fun vectorFormat(vector: RealVector?, headerfooter: Boolean = true): String {
    return matrixFormat(
        vector.let {
            if (it == null) null else MatrixUtils.createColumnRealMatrix(it.toArray())
        }, headerfooter
    )
}

/**
 * mnaFormatNoUnknowns: Prints out the A and Z matrices, printing "x" for the X matrix.
 *
 * @param matrix The A matrix
 * @param knowns The Z matrix
 * @param color If you want colors
 * @return matrix output
 */
fun mnaFormatNoUnknowns(matrix: RealMatrix, knowns: RealVector, color: Boolean = true): String {
    val mftLines = matrixFormat(matrix, false).split("\n").filter { it.isNotBlank() }
    val kftLines = vectorFormat(knowns, false).split("\n").filter { it.isNotBlank() }
    val biggest = kotlin.math.max(mftLines.size, kftLines.size)
    val midpoint = biggest / 2
    val sb = StringBuilder()

    (0 until biggest).withIndex().forEach { entry ->
        if (color) {
            //sb.append(Kolor.foreground(mftLines[entry.value], Color.RED))
            sb.append(if (entry.index == midpoint) " x = " else "     ")
            //sb.append(Kolor.foreground(kftLines[entry.value], Color.BLUE))
        } else {
            sb.append(mftLines[entry.value])
            sb.append(if (entry.index == midpoint) " x = " else "     ")
            sb.append(" ")
            sb.append(kftLines[entry.value])
        }
        sb.append("\n")
    }
    return sb.toString()
}

/**
 * mnaFormat: Prints out the A and Z matrices, printing "x" for the X matrix if it is unavailable
 *
 * @param circuit The circuit to print
 * @return matrix output
 */
fun mnaFormat(circuit: Circuit, color: Boolean = true): String {
    val localMatrix = circuit.matrix
    val localKnowns = circuit.knowns
    if ((localMatrix == null) or (localKnowns == null)) return ""
    return mnaFormat(localMatrix!!, localKnowns!!, circuit.unknowns, color)
}

/**
 * mnaFormat: Prints out the A and Z matrices, printing "x" for the X matrix if it is unavailable
 *
 * @param matrix The A matrix
 * @param knowns The Z matrix
 * @param unknowns The X matrix
 * @param color If you want colors
 * @return matrix output
 */
fun mnaFormat(matrix: RealMatrix, knowns: RealVector, unknowns: RealVector?, color: Boolean = true): String {
    if (unknowns == null) return mnaFormatNoUnknowns(matrix, knowns, color)
    val mftLines = matrixFormat(matrix, false).split("\n").filter { it.isNotBlank() }
    val kftLines = vectorFormat(knowns, false).split("\n").filter { it.isNotBlank() }
    val uftLines = vectorFormat(unknowns, false).split("\n").filter { it.isNotBlank() }
    val biggest = kotlin.math.max(mftLines.size, kotlin.math.max(kftLines.size, uftLines.size))
    val midpoint = biggest / 2
    val even = biggest % 2 == 0
    val sb = StringBuilder()

    (0 until biggest).withIndex().forEach { entry ->
        if (color) {
            //sb.append(Kolor.foreground(mftLines[entry.value], Color.RED))
            sb.append(" ")
            //sb.append(Kolor.foreground(uftLines[entry.value], Color.GREEN))
            sb.append(when {
                !even && entry.index == midpoint -> " = "
                even && entry.index == midpoint - 1 -> " _ "
                even && entry.index == midpoint -> " ‾ "
                else -> "   "
            })
            //sb.append(Kolor.foreground(kftLines[entry.value], Color.BLUE))
        }else{
            sb.append(mftLines[entry.value])
            sb.append(" ")
            sb.append(uftLines[entry.value])
            sb.append(when {
                !even && entry.index == midpoint -> " = "
                even && entry.index == midpoint - 1 -> " _ "
                even && entry.index == midpoint -> " ‾ "
                else -> "   "
            })
            sb.append(kftLines[entry.value])
        }
        sb.append("\n")
    }
    return sb.toString()
}

/**
 * mnaIntelligentFormat: Advanced color coded output of the entire G, B, C, D, v, j, i, and e matrices.
 *
 * @param circuit The circuit to print
 * @return matrix output
 */
@Suppress("UNUSED_PARAMETER") // Remove this line when implementation created.
fun mnaIntelligentFormat(circuit: Circuit): String {
    // TODO: https://github.com/eln2/eln2/issues/88

    // Use https://lpsa.swarthmore.edu/Systems/Electrical/mna/MNA3.html as a guide.
    // Please color the G matrix red. Every other matrix can be a color of your choosing, but please expose every internal matrix.
    // (so, G, B, C, D, v, j, i, e)
    // Tip: use Kolor.foreground("mytext", Color.RED) to color text. Avoid background colors and hard to see foreground colors.
    return ""
}

/**
 * matrixPrint: Print of the A matrix in the MNA
 * @param matrix The A Matrix
 */
@Suppress("unused")
fun matrixPrintln(matrix: RealMatrix) {
    print(matrixFormat(matrix, true))
}

/**
 * knownsPrint: Print of the Z matrix in the MNA
 * @param knowns The Z Matrix
 */
@Suppress("unused")
fun knownsPrintln(knowns: RealVector) {
    print(vectorFormat(knowns, true))
}

/**
 * unknownsPrint: Print of the X matrix in the MNA
 * @param unknowns The X Matrix
 */
@Suppress("unused")
fun unknownsPrintln(unknowns: RealVector?) {
    if (unknowns != null) println(vectorFormat(unknowns, true))
}

/**
 * mnaPrint: Print of all MNA matrices
 * @param matrix The A matrix
 * @param knowns The Z matrix
 * @param unknowns The X matrix
 * @param color Whether to print in color or not
 */
@Suppress("unused")
fun mnaPrintln(matrix: RealMatrix, knowns: RealVector, unknowns: RealVector?, color: Boolean = true) {
    print(mnaFormat(matrix, knowns, unknowns, color))
}

/**
 * mnaPrint: Print of all MNA matrices
 * @param circuit The circuit
 */
@Suppress("unused")
fun mnaPrintln(circuit: Circuit, color: Boolean = true) {
    print(mnaFormat(circuit, color))
}

/**
 * mnaIntelligentPrint: Print of all MNA matrices in a color coded output
 * @param circuit The circuit
 */
@Suppress("unused")
fun mnaIntelligentPrintln(circuit: Circuit) {
    print(mnaIntelligentFormat(circuit))
}
