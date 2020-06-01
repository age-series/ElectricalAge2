package org.eln2.compute.asmComputer.operators

import org.eln2.compute.asmComputer.AsmComputer
import org.eln2.compute.asmComputer.CState
import org.eln2.compute.asmComputer.IntRegister
import org.eln2.compute.asmComputer.Operator
import org.eln2.compute.asmComputer.StringRegister

/**
 * String Length - Literally just copies the length of a string into an int register of your choosing.
 */
class StringLength : Operator() {
    override val OPCODE = "strl"
    override val MIN_ARGS = 2
    override val MAX_ARGS = 2
    override val COST = 0.0

    /**
     * run
     */
    override fun run(opList: List<String>, asmComputer: AsmComputer) {
        val intLengthRegister = opList[0]
        val stringRegister = opList[1]
        if (
            findRegisterType(intLengthRegister, asmComputer) != IntRegister::class ||
            findRegisterType(stringRegister, asmComputer) != StringRegister::class
        ) {
            asmComputer.currState = CState.Errored
            asmComputer.currStateReasoning =
                "$intLengthRegister is not a writable int register, and/or $stringRegister is not a readable string register"
            return
        }
        asmComputer.intRegisters[intLengthRegister]?.contents =
            asmComputer.stringRegisters[stringRegister]?.contents?.length ?: 0
    }
}
