package org.eln2.compute.asmComputer.operators

import org.eln2.compute.asmComputer.AsmComputer
import org.eln2.compute.asmComputer.CState
import org.eln2.compute.asmComputer.Operator
import org.eln2.compute.asmComputer.StringRegister

/**
 * Copy String Part
 *
 * Used to copy a substring of a string register into another buffer.
 */
class CopyStringPart : Operator() {
    override val OPCODE = "strp"
    override val MIN_ARGS = 4
    override val MAX_ARGS = 4
    override val COST = 0.0

    /**
     * run
     */
    override fun run(opList: List<String>, asmComputer: AsmComputer) {
        val toRegister = opList[0]
        val fromRegister = opList[1]
        val beginSlice = opList[2].toIntOrNull()
        val endSlice = opList[3].toIntOrNull()

        if (beginSlice == null || endSlice == null) {
            asmComputer.currState = CState.Errored
            asmComputer.currStateReasoning =
                "String slices must be numbers, $beginSlice and/or $endSlice are not numbers"
            return
        }
        if (
        // determine that we have a writable destination and readable source
            findRegisterType(toRegister, asmComputer) != StringRegister::class ||
            findRegisterType(fromRegister, asmComputer) != StringRegister::class
        ) {
            asmComputer.currState = CState.Errored
            asmComputer.currStateReasoning = "$toRegister and/or $fromRegister are not string registers"
            return
        }

        val data = asmComputer.stringRegisters[fromRegister]?.contents ?: ""
        if (data.length < beginSlice || data.length < endSlice) return
        asmComputer.stringRegisters[toRegister]?.contents = data.substring(beginSlice, endSlice)
    }
}
