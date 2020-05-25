package org.eln2.compute.asmComputer.operators

import org.eln2.compute.asmComputer.AsmComputer
import org.eln2.compute.asmComputer.Operator

/**
 * Code Switch - Switch from Code Register A to Code Register B, or the other way around.
 */
class CodeSwitch : Operator() {
    override val OPCODE = "swch"
    override val MIN_ARGS = 0
    override val MAX_ARGS = 1
    override val COST = 0.0

    /**
     * run
     */
    override fun run(opList: List<String>, asmComputer: AsmComputer) {
        if (asmComputer.codeRegister == "cra") {
            asmComputer.codeRegister = "crb"
        } else {
            asmComputer.codeRegister = "cra"
        }
        if (opList.size == 1) {
            val newPtr = opList[0].toIntOrNull()
            if (newPtr != null) {
                // This is because the computer will step the pointer up by one after this instruction completes.
                asmComputer.ptr = newPtr - 1
            }
        }
    }
}
